package com.pachoclosystem.pachoclosystem.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

@Document(collection = "registros_clinicos")
public class RegistroClinico {

    @Id
    private final String idRegistro;

    @Indexed
    private final String idPaciente;

    private final LocalDateTime fecha;
    private final AutorRegistro autor;
    private final TipoRegistro tipo;
    private final String contenido;
    private final String idMedicamento;
    private final Integer cantidad;

    public RegistroClinico(String idPaciente, TipoRegistro tipo, String contenido, TrabajadorHospital autor, Clock reloj) {
        this(idPaciente, tipo, contenido, autor, null, null, reloj);
    }

    public RegistroClinico(String idPaciente, TipoRegistro tipo, String contenido, TrabajadorHospital autor,
                           String idMedicamento, Integer cantidad, Clock reloj) {
        this(UUID.randomUUID().toString(), idPaciente, LocalDateTime.now(reloj).truncatedTo(ChronoUnit.MICROS),
                AutorRegistro.de(autor), tipo, contenido, idMedicamento, cantidad);
    }

    public RegistroClinico(String idRegistro, String idPaciente, LocalDateTime fecha, AutorRegistro autor, TipoRegistro tipo,
                           String contenido, String idMedicamento, Integer cantidad) {
        this.idRegistro = idRegistro;
        this.idPaciente = idPaciente;
        this.fecha = fecha;
        this.autor = autor;
        this.tipo = tipo;
        this.contenido = contenido;
        this.idMedicamento = idMedicamento;
        this.cantidad = cantidad;
    }

    public String getIdRegistro() {
        return idRegistro;
    }

    public String getIdPaciente() {
        return idPaciente;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public AutorRegistro getAutor() {
        return autor;
    }

    public TipoRegistro getTipo() {
        return tipo;
    }

    public String getContenido() {
        return contenido;
    }

    public String getIdMedicamento() {
        return idMedicamento;
    }

    public Integer getCantidad() {
        return cantidad;
    }
}