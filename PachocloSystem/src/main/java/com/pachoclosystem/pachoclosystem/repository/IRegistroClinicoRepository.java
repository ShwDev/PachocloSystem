package com.pachoclosystem.pachoclosystem.repository;

import com.pachoclosystem.pachoclosystem.model.RegistroClinico;

import java.util.List;

/**
 * Registros del historial clínico. La implementación de la aplicación es
 * {@link RegistroClinicoRepositoryMongo} (MongoDB).
 */
public interface IRegistroClinicoRepository {

    /** Un registro con el id y el nombre actual de su paciente. */
    record RegistroDePaciente(String idPaciente, String nombrePaciente, RegistroClinico registro) {
    }

    /** Añade el registro al historial del paciente (exista activo o dado de baja). */
    void insertar(String idPaciente, RegistroClinico registro);

    /** Registros del paciente en orden de alta. */
    List<RegistroClinico> listarPorPaciente(String idPaciente);

    /**
     * Registros de todos los pacientes activos, ordenados por fecha; a igual
     * fecha, por orden de alta del paciente y del registro.
     */
    List<RegistroDePaciente> listarDePacientesActivos();
}
