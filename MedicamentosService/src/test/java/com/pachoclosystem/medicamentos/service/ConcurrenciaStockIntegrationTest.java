package com.pachoclosystem.medicamentos.service;

import com.pachoclosystem.medicamentos.MongoTestBase;
import com.pachoclosystem.medicamentos.exception.NotFoundException;
import com.pachoclosystem.medicamentos.exception.SolicitudInvalidaException;
import com.pachoclosystem.medicamentos.model.DatosMedicamento;
import com.pachoclosystem.medicamentos.model.Presentacion;
import com.pachoclosystem.medicamentos.repository.IMedicamentoRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Concurrencia contra MongoDB real: varias peticiones a la vez sobre el mismo
 * medicamento nunca dejan el stock negativo, no pierden actualizaciones ni
 * descuentan dos veces, y una edición que espera a un borrado responde 404.
 */
class ConcurrenciaStockIntegrationTest extends MongoTestBase {

    @Autowired
    private MedicamentoService servicio;

    @Autowired
    private SalidasIdempotentesService salidasIdempotentes;

    @Autowired
    private IMedicamentoRepository repositorio;

    @Autowired
    private TransactionTemplate transaccion;

    private final ExecutorService ejecutor = Executors.newFixedThreadPool(16);

    @AfterEach
    void cerrarEjecutor() {
        ejecutor.shutdownNow();
    }

    private String registrar(String lote, int stock) {
        return servicio.registrarMedicamento(new DatosMedicamento("Dolex", "Paracetamol", Presentacion.TABLETA,
                "500 mg", "Genfar", lote, 5, HOY.plusYears(1), "Estante A3"), stock).getIdMedicamento();
    }

    private int stock(String id) {
        return servicio.obtenerMedicamento(id).getCantidadStock();
    }

    /** Lanza las tareas a la vez (todas esperan la misma señal) y devuelve sus resultados. */
    private <T> List<T> aLaVez(List<Callable<T>> tareas) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<T>> futuros = new ArrayList<>();
        for (Callable<T> tarea : tareas) {
            futuros.add(ejecutor.submit(() -> {
                salida.await();
                return tarea.call();
            }));
        }
        salida.countDown();
        List<T> resultados = new ArrayList<>();
        for (Future<T> futuro : futuros) {
            resultados.add(futuro.get(30, TimeUnit.SECONDS));
        }
        return resultados;
    }

    @Test
    void salidasConcurrentesNuncaDejanElStockNegativo() throws Exception {
        String id = registrar("L-1", 100);
        List<Callable<Integer>> tareas = new ArrayList<>();
        for (int hilo = 0; hilo < 16; hilo++) {
            tareas.add(() -> {
                int exitosas = 0;
                for (int intento = 0; intento < 20; intento++) {
                    try {
                        servicio.registrarSalida(id, 1);
                        exitosas++;
                    } catch (SolicitudInvalidaException sinStock) {
                        // esperado cuando se agota
                    }
                }
                return exitosas;
            });
        }

        int totalExitosas = aLaVez(tareas).stream().mapToInt(Integer::intValue).sum();

        assertThat(totalExitosas).isEqualTo(100);
        assertThat(stock(id)).isZero();
    }

    @Test
    void entradasYSalidasConcurrentesNoPierdenActualizaciones() throws Exception {
        String id = registrar("L-1", 1000);
        List<Callable<Void>> tareas = new ArrayList<>();
        for (int hilo = 0; hilo < 16; hilo++) {
            boolean entrada = hilo % 2 == 0;
            tareas.add(() -> {
                for (int vez = 0; vez < 25; vez++) {
                    if (entrada) {
                        servicio.registrarEntrada(id, 3);
                    } else {
                        servicio.registrarSalida(id, 2);
                    }
                }
                return null;
            });
        }

        aLaVez(tareas);

        // 8 hilos x 25 entradas de 3 y 8 hilos x 25 salidas de 2.
        assertThat(stock(id)).isEqualTo(1000 + 8 * 25 * 3 - 8 * 25 * 2);
    }

    @Test
    void clavesDistintasSobreElMismoMedicamentoDescuentanCadaUnaUnaVez() throws Exception {
        String id = registrar("L-1", 50);
        List<Callable<SalidasIdempotentesService.Resultado>> tareas = new ArrayList<>();
        for (int hilo = 0; hilo < 16; hilo++) {
            String clave = "clave-concurrente-%04d".formatted(hilo);
            tareas.add(() -> salidasIdempotentes.registrarSalida(id, 2, clave));
        }

        List<SalidasIdempotentesService.Resultado> resultados = aLaVez(tareas);

        assertThat(resultados).noneMatch(SalidasIdempotentesService.Resultado::repetida);
        assertThat(stock(id)).isEqualTo(50 - 16 * 2);
        assertThat(mongoTemplate.count(new Query(), "idempotencia_salidas")).isEqualTo(16L);
    }

    @Test
    void unaEdicionQueEsperaAUnBorradoRespondeNoEncontrado() throws Exception {
        String id = registrar("L-1", 10);
        CountDownLatch borradoHecho = new CountDownLatch(1);
        CountDownLatch confirmarBorrado = new CountDownLatch(1);

        // Transacción A: borra el medicamento y retiene la fila sin confirmar.
        Future<Boolean> borrado = ejecutor.submit(() -> transaccion.execute(estado -> {
            boolean eliminado = repositorio.eliminar(id);
            borradoHecho.countDown();
            try {
                confirmarBorrado.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException interrumpido) {
                Thread.currentThread().interrupt();
            }
            return eliminado;
        }));
        assertThat(borradoHecho.await(10, TimeUnit.SECONDS)).isTrue();

        // Transacción B: la edición no ve el borrado sin confirmar, pero su escritura
        // espera al bloqueo del documento...
        Future<?> edicion = ejecutor.submit(() -> servicio.editarMedicamento(id,
                new DatosMedicamento("Dolex Forte", "Paracetamol", Presentacion.TABLETA, "500 mg", "Genfar",
                        "L-1", 5, HOY.plusYears(1), "Estante A3")));

        // ...y cuando A confirma, ya no encuentra el medicamento: 404.
        confirmarBorrado.countDown();
        assertThat(borrado.get(10, TimeUnit.SECONDS)).isTrue();
        assertThatThrownBy(() -> edicion.get(10, TimeUnit.SECONDS))
                .hasCauseInstanceOf(NotFoundException.class)
                .cause().hasMessage("No se encontró el medicamento " + id + ".");
    }
}
