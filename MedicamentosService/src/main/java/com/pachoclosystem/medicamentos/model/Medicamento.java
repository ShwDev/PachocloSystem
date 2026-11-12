package com.pachoclosystem.medicamentos.model;

import com.pachoclosystem.medicamentos.exception.SolicitudInvalidaException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * Medicamento en la colección {@code medicamentos}.
 *
 * <p>La unicidad de nombre + concentración + presentación + lote la decide el
 * índice único sobre {@code claveUnica}, que guarda
 * {@link DatosMedicamento#claveUnica()}: la normalización es la de Java. El
 * campo se mantiene junto a los datos (se recalcula al sustituirlos), así el
 * índice no depende de las subclaves de {@code datos}.</p>
 */
@Document(collection = "medicamentos")
public class Medicamento {

    @Id
    private final String idMedicamento;

    /** Clave de unicidad de negocio, persistida para el índice único. */
    @Indexed(unique = true)
    private volatile String claveUnica;

    private volatile DatosMedicamento datos;
    private volatile int cantidadStock;

    public Medicamento(String idMedicamento, DatosMedicamento datos, int cantidadStock) {
        this.idMedicamento = idMedicamento;
        this.claveUnica = datos.claveUnica();
        this.datos = datos;
        this.cantidadStock = cantidadStock;
    }

    /** Suma unidades al stock (doble en memoria del guard del repositorio). */
    public synchronized void ingresarStock(int cantidad) {
        try {
            cantidadStock = Math.addExact(cantidadStock, cantidad);
        } catch (ArithmeticException ex) {
            throw new SolicitudInvalidaException("La cantidad supera el stock máximo admitido.");
        }
    }

    /**
     * Resta unidades del stock si alcanzan (doble en memoria del guard del
     * repositorio).
     */
    public synchronized void retirarStock(int cantidad) {
        if (cantidad > cantidadStock) {
            throw new SolicitudInvalidaException("Stock insuficiente: disponible "
                    + cantidadStock + ", solicitado " + cantidad + ".");
        }
        cantidadStock -= cantidad;
    }

    public boolean tieneStockBajo() {
        return cantidadStock <= datos.stockMinimo();
    }

    public boolean estaVencido(LocalDate hoy) {
        return datos.fechaVencimiento().isBefore(hoy);
    }

    public String getIdMedicamento() {
        return idMedicamento;
    }

    public DatosMedicamento getDatos() {
        return datos;
    }

    /** Sustituye los datos descriptivos y recalcula la clave de unicidad. */
    public void setDatos(DatosMedicamento datos) {
        this.claveUnica = datos.claveUnica();
        this.datos = datos;
    }

    public String getClaveUnica() {
        return claveUnica;
    }

    public int getCantidadStock() {
        return cantidadStock;
    }

    @Override
    public String toString() {
        return "ID: " + idMedicamento + " | " + datos.nombre() + " " + datos.concentracion()
                + " | Lote: " + datos.lote() + " | Stock: " + cantidadStock;
    }
}