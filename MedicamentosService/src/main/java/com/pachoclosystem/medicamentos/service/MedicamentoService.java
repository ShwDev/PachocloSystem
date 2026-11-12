package com.pachoclosystem.medicamentos.service;

import com.pachoclosystem.medicamentos.exception.ConflictoException;
import com.pachoclosystem.medicamentos.exception.NotFoundException;
import com.pachoclosystem.medicamentos.exception.SolicitudInvalidaException;
import com.pachoclosystem.medicamentos.model.DatosMedicamento;
import com.pachoclosystem.medicamentos.model.Medicamento;
import com.pachoclosystem.medicamentos.repository.IMedicamentoRepository;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Reglas de negocio de los medicamentos.
 *
 * <p>No hace falta transacción: cada cambio de un medicamento existente es una
 * única operación atómica sobre su documento —el stock con sus condiciones
 * dentro de la consulta, los datos descriptivos con la clave de unicidad ya
 * calculada—, así que dos peticiones simultáneas nunca dejan el stock en
 * negativo ni pierden una actualización, aunque haya varias instancias del
 * servicio. La transacción de varios documentos queda para las salidas con
 * clave de idempotencia, que la abre {@link SalidasIdempotentesService} y a la
 * que las operaciones del repositorio se unen solos.</p>
 */
@Service
public class MedicamentoService {

    public static final int DIAS_POR_VENCER_MIN = 1;
    public static final int DIAS_POR_VENCER_MAX = 365;

    private static final Comparator<Medicamento> POR_VENCIMIENTO =
            Comparator.comparing((Medicamento m) -> m.getDatos().fechaVencimiento())
                    .thenComparing(Medicamento::getIdMedicamento);

    private final IMedicamentoRepository repositorio;
    private final Clock reloj;

    public MedicamentoService(IMedicamentoRepository repositorio, Clock reloj) {
        this.repositorio = repositorio;
        this.reloj = reloj;
    }

    public Medicamento registrarMedicamento(DatosMedicamento datos, int cantidadStock) {
        Medicamento medicamento = new Medicamento(repositorio.generarNuevoId(), recortar(datos), cantidadStock);
        if (!repositorio.guardar(medicamento)) {
            throw duplicado();
        }
        return medicamento;
    }

    /**
     * Edita los datos descriptivos; el stock no cambia. Si el medicamento se
     * elimina a la vez, la edición responde 404.
     */
    public Medicamento editarMedicamento(String id, DatosMedicamento datos) {
        Medicamento medicamento = obtener(id);
        switch (repositorio.actualizarDatos(medicamento, recortar(datos))) {
            case DUPLICADO -> throw duplicado();
            case NO_EXISTE -> throw noEncontrado(id);
            case ACTUALIZADO -> {
            }
        }
        return medicamento;
    }

    public void eliminarMedicamento(String id) {
        if (!repositorio.eliminar(id)) {
            throw noEncontrado(id);
        }
    }

    public Medicamento obtenerMedicamento(String id) {
        return obtener(id);
    }

    /** Lista medicamentos; si hay texto, filtra por id, nombre o principio activo. */
    public List<Medicamento> listarMedicamentos(String texto) {
        String filtro = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        return repositorio.listarTodos().stream()
                .filter(m -> filtro.isEmpty()
                        || contiene(m.getIdMedicamento(), filtro)
                        || contiene(m.getDatos().nombre(), filtro)
                        || contiene(m.getDatos().principioActivo(), filtro))
                .toList();
    }

    /** Suma unidades al stock; falla si el resultado desbordaría el máximo. */
    public Medicamento registrarEntrada(String id, int cantidad) {
        Medicamento actualizado = repositorio.sumarStock(id, cantidad);
        if (actualizado == null) {
            Medicamento medicamento = repositorio.buscarPorId(id);
            if (medicamento == null) {
                throw noEncontrado(id);
            }
            throw new SolicitudInvalidaException("La cantidad supera el stock máximo admitido.");
        }
        return actualizado;
    }

    /**
     * Resta stock; falla si no alcanza o si el medicamento está vencido. Si ya
     * hay una transacción (salida con clave de idempotencia), las operaciones
     * del repositorio se unen a ella.
     */
    public Medicamento registrarSalida(String id, int cantidad) {
        LocalDate hoy = hoy();
        Medicamento actualizado = repositorio.descontarStock(id, cantidad, hoy);
        if (actualizado == null) {
            Medicamento medicamento = repositorio.buscarPorId(id);
            if (medicamento == null) {
                throw noEncontrado(id);
            }
            if (medicamento.estaVencido(hoy)) {
                throw new SolicitudInvalidaException("No se puede dar salida a un medicamento vencido.");
            }
            throw new SolicitudInvalidaException("Stock insuficiente: disponible "
                    + medicamento.getCantidadStock() + ", solicitado " + cantidad + ".");
        }
        return actualizado;
    }

    /** Medicamentos cuyo stock es igual o menor que su stock mínimo. */
    public List<Medicamento> listarStockBajo() {
        return repositorio.listarTodos().stream()
                .filter(Medicamento::tieneStockBajo)
                .toList();
    }

    /** No vencidos que vencen entre hoy y hoy + {@code dias}, del más próximo al más lejano. */
    public List<Medicamento> listarPorVencer(int dias) {
        if (dias < DIAS_POR_VENCER_MIN || dias > DIAS_POR_VENCER_MAX) {
            throw new SolicitudInvalidaException("El parámetro dias debe ser un entero entre "
                    + DIAS_POR_VENCER_MIN + " y " + DIAS_POR_VENCER_MAX + ".");
        }
        LocalDate hoy = hoy();
        LocalDate limite = hoy.plusDays(dias);
        return repositorio.listarTodos().stream()
                .filter(m -> !m.estaVencido(hoy) && !m.getDatos().fechaVencimiento().isAfter(limite))
                .sorted(POR_VENCIMIENTO)
                .toList();
    }

    /** Medicamentos cuya fecha de vencimiento ya pasó, del más antiguo al más reciente. */
    public List<Medicamento> listarVencidos() {
        LocalDate hoy = hoy();
        return repositorio.listarTodos().stream()
                .filter(m -> m.estaVencido(hoy))
                .sorted(POR_VENCIMIENTO)
                .toList();
    }

    public LocalDate hoy() {
        return LocalDate.now(reloj);
    }

    private Medicamento obtener(String id) {
        Medicamento medicamento = repositorio.buscarPorId(id);
        if (medicamento == null) {
            throw noEncontrado(id);
        }
        return medicamento;
    }

    private static DatosMedicamento recortar(DatosMedicamento d) {
        return new DatosMedicamento(d.nombre().trim(), d.principioActivo().trim(), d.presentacion(),
                d.concentracion().trim(), d.laboratorio().trim(), d.lote().trim(), d.stockMinimo(),
                d.fechaVencimiento(), d.ubicacion().trim());
    }

    private static boolean contiene(String valor, String filtro) {
        return valor.toLowerCase(Locale.ROOT).contains(filtro);
    }

    private NotFoundException noEncontrado(String id) {
        return new NotFoundException("No se encontró el medicamento " + id + ".");
    }

    private ConflictoException duplicado() {
        return new ConflictoException(
                "Ya existe un medicamento con el mismo nombre, concentración, presentación y lote.");
    }
}
