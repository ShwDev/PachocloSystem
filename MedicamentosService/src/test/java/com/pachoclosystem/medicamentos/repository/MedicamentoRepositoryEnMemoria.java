package com.pachoclosystem.medicamentos.repository;

import com.pachoclosystem.medicamentos.exception.SolicitudInvalidaException;
import com.pachoclosystem.medicamentos.model.DatosMedicamento;
import com.pachoclosystem.medicamentos.model.Medicamento;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Doble en memoria de {@link IMedicamentoRepository} para los tests unitarios de
 * las reglas de negocio, sin base de datos. Reproduce el contrato de
 * {@link MedicamentoRepositoryMongo} (ids, clave única, orden y guardas de
 * stock), pero no su concurrencia: esa se prueba contra MongoDB.
 *
 * <p>Sin {@code @Repository}: no debe registrarse como bean en los tests con
 * contexto Spring, que usan el repositorio MongoDB.</p>
 */
public class MedicamentoRepositoryEnMemoria implements IMedicamentoRepository {

    private static final String PREFIJO_ID = "MED";

    /** ID de medicamento -> medicamento. */
    private final Map<String, Medicamento> medicamentos = new ConcurrentHashMap<>();
    /** Clave única normalizada -> ID de medicamento. */
    private final Map<String, String> indicePorClave = new ConcurrentHashMap<>();

    private final AtomicInteger contadorId = new AtomicInteger(1);

    @Override
    public String generarNuevoId() {
        return String.format("%s-%04d", PREFIJO_ID, contadorId.getAndIncrement());
    }

    @Override
    public synchronized boolean guardar(Medicamento medicamento) {
        if (medicamento == null || medicamentos.containsKey(medicamento.getIdMedicamento())) {
            return false;
        }
        String clave = medicamento.getDatos().claveUnica();
        if (indicePorClave.putIfAbsent(clave, medicamento.getIdMedicamento()) != null) {
            return false;
        }
        medicamentos.put(medicamento.getIdMedicamento(), medicamento);
        return true;
    }

    @Override
    public synchronized ResultadoActualizacion actualizarDatos(Medicamento medicamento,
                                                               DatosMedicamento nuevosDatos) {
        String id = medicamento.getIdMedicamento();
        Medicamento guardado = medicamentos.get(id);
        if (guardado == null) {
            return ResultadoActualizacion.NO_EXISTE;
        }
        String claveVieja = guardado.getDatos().claveUnica();
        String claveNueva = nuevosDatos.claveUnica();
        String dueno = indicePorClave.putIfAbsent(claveNueva, id);
        if (dueno != null && !dueno.equals(id)) {
            return ResultadoActualizacion.DUPLICADO;
        }
        if (!claveNueva.equals(claveVieja)) {
            indicePorClave.remove(claveVieja, id);
        }
        guardado.setDatos(nuevosDatos);
        medicamento.setDatos(nuevosDatos);
        return ResultadoActualizacion.ACTUALIZADO;
    }

    @Override
    public synchronized Medicamento sumarStock(String id, int cantidad) {
        Medicamento guardado = buscarPorId(id);
        if (guardado == null) {
            return null;
        }
        try {
            guardado.ingresarStock(cantidad);
            return guardado;
        } catch (SolicitudInvalidaException desborda) {
            return null;
        }
    }

    @Override
    public synchronized Medicamento descontarStock(String id, int cantidad, LocalDate hoy) {
        Medicamento guardado = buscarPorId(id);
        if (guardado == null || guardado.estaVencido(hoy)) {
            return null;
        }
        try {
            guardado.retirarStock(cantidad);
            return guardado;
        } catch (SolicitudInvalidaException sinStock) {
            return null;
        }
    }

    @Override
    public Medicamento buscarPorId(String id) {
        return id == null ? null : medicamentos.get(id);
    }

    @Override
    public List<Medicamento> listarTodos() {
        List<Medicamento> listados = new ArrayList<>(medicamentos.values());
        listados.sort(Comparator.comparing(Medicamento::getIdMedicamento));
        return listados;
    }

    @Override
    public synchronized boolean eliminar(String id) {
        Medicamento eliminado = id == null ? null : medicamentos.remove(id);
        if (eliminado == null) {
            return false;
        }
        indicePorClave.remove(eliminado.getDatos().claveUnica(), id);
        return true;
    }
}
