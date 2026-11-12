package com.pachoclosystem.medicamentos.repository;

import com.pachoclosystem.medicamentos.model.DatosMedicamento;
import com.pachoclosystem.medicamentos.model.Medicamento;

import java.time.LocalDate;
import java.util.List;

/**
 * Almacenamiento de medicamentos. La implementación de la aplicación es
 * {@link MedicamentoRepositoryMongo} (MongoDB).
 *
 * <p>No hay bloqueos de fila ni lecturas para actualizar: cada cambio de stock
 * es una única operación atómica sobre el documento del medicamento (un
 * {@code findAndModify} con las condiciones dentro de la consulta), así que dos
 * salidas simultáneas nunca dejan el stock en negativo ni pierden una
 * actualización, aunque haya varias instancias del servicio.</p>
 */
public interface IMedicamentoRepository {

    /** Resultado de {@link #actualizarDatos}. */
    enum ResultadoActualizacion {
        ACTUALIZADO,
        /** Los nuevos datos chocan con la clave única de otro medicamento: no cambia nada. */
        DUPLICADO,
        /** El medicamento ya no existe (p. ej. se eliminó a la vez). */
        NO_EXISTE
    }

    String generarNuevoId();

    /**
     * Inserta un medicamento nuevo. Devuelve {@code false} (y no guarda nada) si
     * otro medicamento ya tiene su misma clave única o su id.
     */
    boolean guardar(Medicamento medicamento);

    /**
     * Sustituye los datos descriptivos de un medicamento (no el stock) y, si se
     * aplican, también los del objeto recibido.
     */
    ResultadoActualizacion actualizarDatos(Medicamento medicamento, DatosMedicamento nuevosDatos);

    /**
     * Suma {@code cantidad} unidades al stock del medicamento, de forma atómica
     * y solo si el resultado no desborda el máximo admitido (int de 32 bits).
     *
     * @return el medicamento con el stock ya sumado, o {@code null} si no existe
     *         o la suma desbordaría el máximo
     */
    Medicamento sumarStock(String id, int cantidad);

    /**
     * Resta {@code cantidad} unidades al stock del medicamento si alcanzan y el
     * medicamento no está vencido a fecha de {@code hoy}, de forma atómica: una
     * salida concurrente que dejara el stock por debajo de la cantidad no se
     * aplica.
     *
     * @return el medicamento con el stock ya restado, o {@code null} si no
     *         existe, no alcanza el stock o está vencido
     */
    Medicamento descontarStock(String id, int cantidad, LocalDate hoy);

    Medicamento buscarPorId(String id);

    /** Todos los medicamentos, ordenados por id. */
    List<Medicamento> listarTodos();

    boolean eliminar(String id);
}
