package com.pachoclosystem.pachoclosystem.repository;

import com.pachoclosystem.pachoclosystem.model.Paciente;

import java.util.List;

/**
 * Pacientes. La implementación de la aplicación es {@link PacienteRepositoryMongo}
 * (MongoDB). Los registros clínicos tienen su propio repositorio
 * ({@link IRegistroClinicoRepository}).
 *
 * <p>Las modificaciones solo se aplican a pacientes activos: si el paciente no
 * existe o se dio de baja, devuelven {@code false} sin cambiar nada.</p>
 */
public interface IPacienteRepository {

    String generarNuevoId();

    /** Activo o dado de baja; {@code null} si no existe. */
    Paciente buscarPorId(String id);

    /** Inserta un paciente nuevo. */
    boolean guardarPaciente(Paciente p);

    boolean actualizarDatos(String idPaciente, String nombre, int edad, int habitacion);

    boolean actualizarHabitacion(String idPaciente, int habitacion);

    /** Baja lógica (activo = false). {@code false} si no existía o ya estaba de baja. */
    boolean darDeBaja(String idPaciente);

    /** Todos, activos y dados de baja, en orden de alta. */
    List<Paciente> obtenerTodos();
}
