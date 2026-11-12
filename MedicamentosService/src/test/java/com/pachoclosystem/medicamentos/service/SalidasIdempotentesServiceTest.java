package com.pachoclosystem.medicamentos.service;

import com.pachoclosystem.medicamentos.MongoTestBase;
import com.pachoclosystem.medicamentos.exception.ConflictoException;
import com.pachoclosystem.medicamentos.exception.SolicitudInvalidaException;
import com.pachoclosystem.medicamentos.model.DatosMedicamento;
import com.pachoclosystem.medicamentos.model.Presentacion;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Query;

import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Salidas con clave de idempotencia contra MongoDB real (claves y
 * transacciones de verdad), con la fecha fijada.
 */
class SalidasIdempotentesServiceTest extends MongoTestBase {

    private static final String CLAVE = "8f14e45f-ceea-4672-a5b1-7a0c3c9e2f01";

    @Autowired
    private MedicamentoService medicamentos;

    @Autowired
    private SalidasIdempotentesService salidas;

    private String dolex;
    private String amoxil;

    @BeforeEach
    void preparar() {
        dolex = registrar("Dolex", "L-1", 20, HOY.plusYears(1));
        amoxil = registrar("Amoxil", "L-2", 20, HOY.plusYears(1));
    }

    private String registrar(String nombre, String lote, int stock, LocalDate vencimiento) {
        return medicamentos.registrarMedicamento(new DatosMedicamento(nombre, "Paracetamol",
                Presentacion.TABLETA, "500 mg", "Genfar", lote, 2, vencimiento, "Estante A3"), stock)
                .getIdMedicamento();
    }

    private int stock(String id) {
        return medicamentos.obtenerMedicamento(id).getCantidadStock();
    }

    @Test
    void laMismaClaveDosVecesDescuentaUnaSolaVezYDevuelveLaMismaRespuesta() {
        SalidasIdempotentesService.Resultado primera = salidas.registrarSalida(dolex, 3, CLAVE);
        SalidasIdempotentesService.Resultado segunda = salidas.registrarSalida(dolex, 3, CLAVE);

        assertThat(stock(dolex)).isEqualTo(17);
        assertThat(primera.repetida()).isFalse();
        assertThat(segunda.repetida()).isTrue();
        assertThat(segunda.respuesta()).isEqualTo(primera.respuesta());
        assertThat(segunda.respuesta().cantidadStock()).isEqualTo(17);
    }

    @Test
    void laRespuestaRepetidaEsLaDeEntoncesAunqueElStockHayaCambiado() {
        SalidasIdempotentesService.Resultado primera = salidas.registrarSalida(dolex, 3, CLAVE);
        medicamentos.registrarEntrada(dolex, 10);

        assertThat(salidas.registrarSalida(dolex, 3, CLAVE).respuesta()).isEqualTo(primera.respuesta());
        assertThat(stock(dolex)).isEqualTo(27);
    }

    @Test
    void laMismaClaveConOtraCantidadDa409SinDescontar() {
        salidas.registrarSalida(dolex, 3, CLAVE);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> salidas.registrarSalida(dolex, 4, CLAVE))
                .withMessage("La clave de idempotencia ya se usó para otra salida de stock con otro "
                        + "medicamento o cantidad.");
        assertThat(stock(dolex)).isEqualTo(17);
    }

    @Test
    void laMismaClaveConOtroMedicamentoDa409SinDescontar() {
        salidas.registrarSalida(dolex, 3, CLAVE);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> salidas.registrarSalida(amoxil, 3, CLAVE));
        assertThat(stock(amoxil)).isEqualTo(20);
    }

    @Test
    void unaSalidaFallidaNoSeGuardaYSuReintentoSeReevalua() {
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> salidas.registrarSalida(dolex, 25, CLAVE))
                .withMessage("Stock insuficiente: disponible 20, solicitado 25.");

        medicamentos.registrarEntrada(dolex, 10);
        SalidasIdempotentesService.Resultado reintento = salidas.registrarSalida(dolex, 25, CLAVE);

        assertThat(reintento.repetida()).isFalse();
        assertThat(stock(dolex)).isEqualTo(5);
    }

    @Test
    void laClaveSeRecuerda24HorasYDespuesSeTrataComoNueva() {
        salidas.registrarSalida(dolex, 3, CLAVE);

        // Un instante antes de las 24 h: sigue siendo la misma salida (y otra cantidad da 409).
        reloj.avanzar(Duration.ofHours(24).minusMillis(1));
        assertThat(salidas.registrarSalida(dolex, 3, CLAVE).repetida()).isTrue();
        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> salidas.registrarSalida(dolex, 5, CLAVE));
        assertThat(stock(dolex)).isEqualTo(17);

        // A las 24 h caduca: la misma clave hace una salida nueva y vuelve a descontar.
        reloj.avanzar(Duration.ofMillis(1));
        SalidasIdempotentesService.Resultado nueva = salidas.registrarSalida(dolex, 5, CLAVE);
        assertThat(nueva.repetida()).isFalse();
        assertThat(stock(dolex)).isEqualTo(12);
        assertThat(mongoTemplate.count(new Query(), "idempotencia_salidas")).isEqualTo(1L);
    }

    @Test
    void unMedicamentoVencidoNoGuardaLaClave() {
        String vencido = registrar("Viejo", "L-3", 20, HOY.minusDays(1));

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> salidas.registrarSalida(vencido, 1, CLAVE));
        // La clave sigue libre: se puede usar para otra salida sin 409.
        assertThat(salidas.registrarSalida(dolex, 1, CLAVE).repetida()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "corta-123", "tiene espacios dentro 1234", "con-tilde-ñ-1234567", "a/b/c/d/e/f/g/h/i"})
    void unaClaveConFormatoInvalidoDa400SinDescontar(String clave) {
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> salidas.registrarSalida(dolex, 1, clave))
                .withMessageContaining("La cabecera Idempotency-Key debe tener entre 16 y 100 caracteres");
        assertThat(stock(dolex)).isEqualTo(20);
    }

    @Test
    void unaClaveDeMasDe100CaracteresDa400() {
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> salidas.registrarSalida(dolex, 1, "a".repeat(101)));
        assertThat(salidas.registrarSalida(dolex, 1, "a".repeat(100)).repetida()).isFalse();
    }

    @Test
    void peticionesSimultaneasConLaMismaClaveDescuentanUnaVezYRecibenLaMismaRespuesta()
            throws Exception {
        int hilos = 16;
        ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<SalidasIdempotentesService.Resultado>> resultados = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                resultados.add(ejecutor.submit(() -> {
                    salida.await();
                    return salidas.registrarSalida(dolex, 2, CLAVE);
                }));
            }
            salida.countDown();

            List<SalidasIdempotentesService.Resultado> obtenidos = new ArrayList<>();
            for (Future<SalidasIdempotentesService.Resultado> resultado : resultados) {
                obtenidos.add(resultado.get(10, TimeUnit.SECONDS));
            }

            assertThat(stock(dolex)).isEqualTo(18);
            assertThat(obtenidos).extracting(SalidasIdempotentesService.Resultado::respuesta)
                    .containsOnly(obtenidos.getFirst().respuesta());
            assertThat(obtenidos).filteredOn(r -> !r.repetida()).hasSize(1);
        } finally {
            ejecutor.shutdownNow();
        }
    }
}
