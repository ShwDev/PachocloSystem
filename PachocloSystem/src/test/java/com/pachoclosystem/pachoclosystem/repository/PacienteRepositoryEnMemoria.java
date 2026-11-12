package com.pachoclosystem.pachoclosystem.repository;

import com.pachoclosystem.pachoclosystem.model.Paciente;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Doble en memoria de {@link IPacienteRepository} para los tests unitarios, sin
 * base de datos. Reproduce el contrato de {@link PacienteRepositoryMongo}.
 */
public class PacienteRepositoryEnMemoria implements IPacienteRepository {

    private final Map<String, Paciente> pacientes = new LinkedHashMap<>();
    private int contadorId = 1;

    @Override
    public synchronized String generarNuevoId() {
        return String.format("PAC-%04d", contadorId++);
    }

    @Override
    public synchronized Paciente buscarPorId(String id) {
        return pacientes.get(id);
    }

    @Override
    public synchronized boolean guardarPaciente(Paciente p) {
        pacientes.put(p.getIdPaciente(), p);
        return true;
    }

    @Override
    public synchronized boolean actualizarDatos(String idPaciente, String nombre, int edad, int habitacion) {
        Paciente paciente = activo(idPaciente);
        if (paciente == null) {
            return false;
        }
        paciente.actualizarDatos(nombre, edad, habitacion);
        return true;
    }

    @Override
    public synchronized boolean actualizarHabitacion(String idPaciente, int habitacion) {
        Paciente paciente = activo(idPaciente);
        if (paciente == null) {
            return false;
        }
        paciente.actualizarDatos(habitacion);
        return true;
    }

    @Override
    public synchronized boolean darDeBaja(String idPaciente) {
        Paciente paciente = activo(idPaciente);
        if (paciente == null) {
            return false;
        }
        paciente.desactivar();
        return true;
    }

    @Override
    public synchronized List<Paciente> obtenerTodos() {
        return new ArrayList<>(pacientes.values());
    }

    private Paciente activo(String idPaciente) {
        Paciente paciente = pacientes.get(idPaciente);
        return paciente != null && paciente.isActivo() ? paciente : null;
    }
}
