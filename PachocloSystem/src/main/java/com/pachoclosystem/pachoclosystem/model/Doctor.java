package com.pachoclosystem.pachoclosystem.model;

import org.springframework.data.annotation.TypeAlias;

@TypeAlias("DOCTOR")
public class Doctor extends TrabajadorHospital {
    private String especialidad;

    public Doctor(String idTrabajador, String nombreCompleto, String especialidad) {
        super(idTrabajador, nombreCompleto);
        this.especialidad = especialidad;
    }

    public String getEspecialidad() {
        return especialidad;
    }

    public void setEspecialidad(String especialidad) {
        this.especialidad = especialidad;
    }
}