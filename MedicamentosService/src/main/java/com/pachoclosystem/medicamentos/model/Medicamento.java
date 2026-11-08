package com.pachoclosystem.medicamentos.model;

import com.pachoclosystem.medicamentos.exception.SolicitudInvalidaException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDate;

/**
 * Entidad principal de Medicamento mapeada como Documento MongoDB en la colección "medicamentos".
 * Se incluye un índice compuesto único para replicar la restricción de negocio (nombre + concentración + presentación + lote).
 */
@Document(collection = "medicamentos")
@CompoundIndex(
    name = "idx_medicamento_clave_unica", 
    def = "{'datos.nombre': 1, 'datos.concentracion': 1, 'datos.presentacion': 1, 'datos.lote': 1}", 
    unique = true
)
public class Medicamento {

    @Id
    private final String idMedicamento;
    private volatile DatosMedicamento datos;
    private volatile int cantidadStock;

    public Medicamento(String idMedicamento, DatosMedicamento datos, int cantidadStock) {
        this.idMedicamento = idMedicamento;
        this.datos = datos;
        this.cantidadStock = cantidadStock;
    }

    /** Suma unidades al stock. */
    public synchronized void ingresarStock(int cantidad) {
        try {
            cantidadStock = Math.addExact(cantidadStock, cantidad);
        } catch (ArithmeticException ex) {
            throw new SolicitudInvalidaException("La cantidad supera el stock máximo admitido.");
        }
    }

    /**
     * Resta unidades del stock si alcanzan.
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

    public void setDatos(DatosMedicamento datos) {
        this.datos = datos;
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