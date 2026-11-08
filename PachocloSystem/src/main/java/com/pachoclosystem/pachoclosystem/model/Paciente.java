package com.pachoclosystem.pachoclosystem.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Paciente mapeado como Documento MongoDB en la colección "pacientes".
 */
@Document(collection = "pacientes")
public class Paciente {

    @Id
    private final String idPaciente;
    private volatile String nombre;
    private volatile int edad;
    private volatile int habitacion;
    private volatile boolean activo;

    public Paciente(String idPaciente, String nombre, int edad, int habitacion) {
        this(idPaciente, nombre, edad, habitacion, true);
    }

    public Paciente(String idPaciente, String nombre, int edad, int habitacion, boolean activo) {
        this.idPaciente = idPaciente;
        this.nombre = nombre;
        this.edad = edad;
        this.habitacion = habitacion;
        this.activo = activo;
    }

    public void actualizarDatos(int nuevaHabitacion) {
        this.habitacion = nuevaHabitacion;
    }

    public void actualizarDatos(String nuevoNombre, int nuevaEdad, int nuevaHabitacion) {
        this.nombre = nuevoNombre;
        this.edad = nuevaEdad;
        this.habitacion = nuevaHabitacion;
    }

    public String getIdPaciente() {
        return idPaciente;
    }

    public boolean isActivo() {
        return activo;
    }

    public void desactivar() {
        this.activo = false;
    }

    public String getNombre() {
        return nombre;
    }

    public int getEdad() {
        return edad;
    }

    public int getHabitacion() {
        return habitacion;
    }

    @Override
    public String toString() {
        return "ID: " + idPaciente + " | Nombre: " + nombre + " | Edad: " + edad
                + " | Habitación: " + habitacion;
    }
}   