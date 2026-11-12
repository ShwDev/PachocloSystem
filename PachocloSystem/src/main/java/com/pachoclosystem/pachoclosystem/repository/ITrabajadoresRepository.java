package com.pachoclosystem.pachoclosystem.repository;

import com.pachoclosystem.pachoclosystem.model.TrabajadorHospital;

import java.util.List;

/**
 * Trabajadores (doctores y enfermeros). La implementación de la aplicación es
 * {@link TrabajadorRepositoryMongo} (MongoDB).
 */
public interface ITrabajadoresRepository {

    /** Siguiente id con ese prefijo (DOC o ENF), p. ej. DOC-0001. */
    String generarNuevoId(String prefijo);

    /** Inserta un trabajador nuevo. */
    boolean guardarTrabajador(TrabajadorHospital u);

    /**
     * Guarda los datos editables (nombre y especialidad o nivel; el tipo no cambia).
     *
     * @return {@code false} si el trabajador ya no existe
     */
    boolean actualizarTrabajador(TrabajadorHospital t);

    TrabajadorHospital buscarPorId(String id);

    /**
     * Como {@link #buscarPorId(String)}, pero bloquea al trabajador hasta el final
     * de la transacción en curso: otra petición que quiera modificarlo o
     * eliminarlo espera.
     */
    TrabajadorHospital buscarPorIdParaActualizar(String id);

    boolean eliminarTrabajador(String id);

    /** Todos, en orden de alta. */
    List<TrabajadorHospital> obtenerTodos();
}
