package com.pachoclosystem.pachoclosystem.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "trabajadores")
public abstract class TrabajadorHospital {

    @Id
    protected String idTrabajador;
    protected String nombreCompleto;

    public TrabajadorHospital(String idTrabajador, String nombreCompleto){
        this.idTrabajador = idTrabajador;
        this.nombreCompleto = nombreCompleto;   
    }

    public String getIdTrabajador() {
        return idTrabajador;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }
}