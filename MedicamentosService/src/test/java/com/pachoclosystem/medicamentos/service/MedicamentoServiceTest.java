package com.pachoclosystem.medicamentos.service;

import com.pachoclosystem.medicamentos.exception.ConflictoException;
import com.pachoclosystem.medicamentos.exception.NotFoundException;
import com.pachoclosystem.medicamentos.exception.SolicitudInvalidaException;
import com.pachoclosystem.medicamentos.model.DatosMedicamento;
import com.pachoclosystem.medicamentos.model.Medicamento;
import com.pachoclosystem.medicamentos.model.Presentacion;
import com.pachoclosystem.medicamentos.repository.MedicamentoRepositoryEnMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/**
 * Reglas de negocio de los medicamentos, sin base de datos (doble en memoria) y
 * con la fecha fijada. La concurrencia se prueba contra MongoDB en
 * {@code ConcurrenciaStockIntegrationTest}.
 */
class MedicamentoServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 6, 15);

    private MedicamentoRepositoryEnMemoria repositorio;
    private MedicamentoService servicio;

    @BeforeEach
    void preparar() {
        repositorio = new MedicamentoRepositoryEnMemoria();
        Clock reloj = Clock.fixed(HOY.atStartOfDay().toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        servicio = new MedicamentoService(repositorio, reloj);
    }

    private static DatosMedicamento datos(String nombre, String lote, int stockMinimo, LocalDate vencimiento) {
        return new DatosMedicamento(nombre, "Paracetamol", Presentacion.TABLETA, "500 mg",
                "Genfar", lote, stockMinimo, vencimiento, "Farmacia - Estante A3");
    }

    private Medicamento registrar(String nombre, String lote, int stock, int stockMinimo, LocalDate vencimiento) {
        return servicio.registrarMedicamento(datos(nombre, lote, stockMinimo, vencimiento), stock);
    }

    @Test
    void registrarGeneraIdsConPrefijoMed() {
        Medicamento primero = registrar("Dolex", "L-1", 100, 10, HOY.plusYears(1));
        Medicamento segundo = registrar("Dolex", "L-2", 50, 10, HOY.plusYears(1));

        assertThat(primero.getIdMedicamento()).isEqualTo("MED-0001");
        assertThat(segundo.getIdMedicamento()).isEqualTo("MED-0002");
        assertThat(primero.getCantidadStock()).isEqualTo(100);
        assertThat(primero.getDatos().lote()).isEqualTo("L-1");
    }

    @Test
    void registrarRecortaLosTextos() {
        DatosMedicamento conEspacios = new DatosMedicamento("  Dolex ", " Paracetamol ", Presentacion.TABLETA,
                " 500 mg ", " Genfar ", " L-1 ", 10, HOY.plusYears(1), "  Estante A3  ");

        Medicamento medicamento = servicio.registrarMedicamento(conEspacios, 5);

        DatosMedicamento d = medicamento.getDatos();
        assertThat(d.nombre()).isEqualTo("Dolex");
        assertThat(d.principioActivo()).isEqualTo("Paracetamol");
        assertThat(d.concentracion()).isEqualTo("500 mg");
        assertThat(d.laboratorio()).isEqualTo("Genfar");
        assertThat(d.lote()).isEqualTo("L-1");
        assertThat(d.ubicacion()).isEqualTo("Estante A3");
    }

    @Test
    void registrarDuplicadoLanzaConflictoSinImportarMayusculas() {
        registrar("Dolex", "L-1", 100, 10, HOY.plusYears(1));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> registrar("DOLEX", "l-1", 5, 1, HOY.plusMonths(3)))
                .withMessage("Ya existe un medicamento con el mismo nombre, concentración, presentación y lote.");
        assertThat(servicio.listarMedicamentos(null)).hasSize(1);
    }

    @Test
    void registrarLoteYaVencidoEsValido() {
        Medicamento medicamento = registrar("Dolex", "L-1", 100, 10, HOY.minusDays(1));

        assertThat(medicamento.getDatos().fechaVencimiento()).isEqualTo(HOY.minusDays(1));
        assertThat(medicamento.estaVencido(HOY)).isTrue();
        assertThat(servicio.listarVencidos()).containsExactly(medicamento);
    }

    @Test
    void registrarMedicamentoQueVenceHoyEsValido() {
        assertThat(registrar("Dolex", "L-1", 100, 10, HOY).getDatos().fechaVencimiento()).isEqualTo(HOY);
    }

    @Test
    void editarActualizaDatosSinTocarElStock() {
        Medicamento medicamento = registrar("Dolex", "L-1", 100, 10, HOY.plusYears(1));

        Medicamento editado = servicio.editarMedicamento(medicamento.getIdMedicamento(),
                datos("Dolex Forte", "L-9", 20, HOY.plusMonths(6)));

        assertThat(editado.getDatos().nombre()).isEqualTo("Dolex Forte");
        assertThat(editado.getDatos().lote()).isEqualTo("L-9");
        assertThat(editado.getDatos().stockMinimo()).isEqualTo(20);
        assertThat(editado.getCantidadStock()).isEqualTo(100);
    }

    @Test
    void editarHaciaLaClaveDeOtroLanzaConflictoYNoCambiaNada() {
        registrar("Dolex", "L-1", 100, 10, HOY.plusYears(1));
        Medicamento otro = registrar("Dolex", "L-2", 50, 10, HOY.plusYears(1));

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.editarMedicamento(otro.getIdMedicamento(),
                        datos("Dolex", "L-1", 10, HOY.plusYears(1))));
        assertThat(otro.getDatos().lote()).isEqualTo("L-2");
    }

    @Test
    void editarConSuPropiaClaveEsValido() {
        Medicamento medicamento = registrar("Dolex", "L-1", 100, 10, HOY.plusYears(1));

        servicio.editarMedicamento(medicamento.getIdMedicamento(), datos("Dolex", "L-1", 30, HOY.plusYears(1)));

        assertThat(medicamento.getDatos().stockMinimo()).isEqualTo(30);
    }

    @Test
    void operacionesSobreIdInexistenteLanzanNotFound() {
        String mensaje = "No se encontró el medicamento MED-9999.";
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.obtenerMedicamento("MED-9999")).withMessage(mensaje);
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.editarMedicamento("MED-9999", datos("Dolex", "L-1", 1, HOY)))
                .withMessage(mensaje);
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.eliminarMedicamento("MED-9999")).withMessage(mensaje);
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.registrarEntrada("MED-9999", 1)).withMessage(mensaje);
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.registrarSalida("MED-9999", 1)).withMessage(mensaje);
    }

    @Test
    void eliminarLoQuitaYLiberaSuClave() {
        Medicamento medicamento = registrar("Dolex", "L-1", 100, 10, HOY.plusYears(1));

        servicio.eliminarMedicamento(medicamento.getIdMedicamento());

        assertThat(repositorio.buscarPorId(medicamento.getIdMedicamento())).isNull();
        assertThat(registrar("Dolex", "L-1", 1, 1, HOY.plusYears(1)).getIdMedicamento()).isEqualTo("MED-0002");
    }

    @Test
    void listarFiltraPorIdNombreOPrincipioActivo() {
        registrar("Dolex", "L-1", 100, 10, HOY.plusYears(1));
        servicio.registrarMedicamento(new DatosMedicamento("Amoxil", "Amoxicilina", Presentacion.CAPSULA,
                "500 mg", "GSK", "A-1", 5, HOY.plusYears(1), "Estante B1"), 20);

        assertThat(servicio.listarMedicamentos(null)).hasSize(2);
        assertThat(servicio.listarMedicamentos("  DOLEX ")).hasSize(1);
        assertThat(servicio.listarMedicamentos("amoxicilina")).first()
                .extracting(Medicamento::getIdMedicamento).isEqualTo("MED-0002");
        assertThat(servicio.listarMedicamentos("med-0001")).hasSize(1);
        assertThat(servicio.listarMedicamentos("zzz")).isEmpty();
    }

    @Test
    void entradaSumaAlStock() {
        Medicamento medicamento = registrar("Dolex", "L-1", 10, 5, HOY.plusYears(1));

        servicio.registrarEntrada(medicamento.getIdMedicamento(), 15);

        assertThat(medicamento.getCantidadStock()).isEqualTo(25);
    }

    @Test
    void entradaQueDesbordaElEnteroLanzaSolicitudInvalida() {
        Medicamento medicamento = registrar("Dolex", "L-1", Integer.MAX_VALUE, 5, HOY.plusYears(1));

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.registrarEntrada(medicamento.getIdMedicamento(), 1));
        assertThat(medicamento.getCantidadStock()).isEqualTo(Integer.MAX_VALUE);
    }

    @Test
    void salidaPorTodoElStockLoDejaEnCero() {
        Medicamento medicamento = registrar("Dolex", "L-1", 10, 5, HOY.plusYears(1));

        servicio.registrarSalida(medicamento.getIdMedicamento(), 10);

        assertThat(medicamento.getCantidadStock()).isZero();
    }

    @Test
    void salidaMayorAlStockLanzaSolicitudInvalidaYNoDescuenta() {
        Medicamento medicamento = registrar("Dolex", "L-1", 10, 5, HOY.plusYears(1));

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.registrarSalida(medicamento.getIdMedicamento(), 11))
                .withMessage("Stock insuficiente: disponible 10, solicitado 11.");
        assertThat(medicamento.getCantidadStock()).isEqualTo(10);
    }

    @Test
    void salidaDeMedicamentoVencidoLanzaSolicitudInvalida() {
        Medicamento medicamento = registrar("Dolex", "L-1", 10, 5, HOY.plusYears(1));
        servicio.editarMedicamento(medicamento.getIdMedicamento(), datos("Dolex", "L-1", 5, HOY.minusDays(1)));

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.registrarSalida(medicamento.getIdMedicamento(), 1))
                .withMessage("No se puede dar salida a un medicamento vencido.");
    }

    @Test
    void stockBajoIncluyeLosQueEstanEnOPorDebajoDelMinimo() {
        registrar("Dolex", "L-1", 10, 10, HOY.plusYears(1));
        registrar("Dolex", "L-2", 3, 10, HOY.plusYears(1));
        registrar("Dolex", "L-3", 11, 10, HOY.plusYears(1));

        assertThat(servicio.listarStockBajo()).extracting(m -> m.getDatos().lote())
                .containsExactly("L-1", "L-2");
    }

    @Test
    void porVencerIncluyeLosLimitesYOrdenaPorFecha() {
        registrar("Dolex", "L-30", 1, 0, HOY.plusDays(30));
        registrar("Dolex", "L-31", 1, 0, HOY.plusDays(31));
        registrar("Dolex", "L-0", 1, 0, HOY);
        registrar("Dolex", "L-10", 1, 0, HOY.plusDays(10));

        assertThat(servicio.listarPorVencer(30)).extracting(m -> m.getDatos().lote())
                .containsExactly("L-0", "L-10", "L-30");
        assertThat(servicio.listarPorVencer(31)).hasSize(4);
    }

    @Test
    void porVencerExcluyeLosYaVencidos() {
        Medicamento medicamento = registrar("Dolex", "L-1", 1, 0, HOY.plusDays(5));
        servicio.editarMedicamento(medicamento.getIdMedicamento(), datos("Dolex", "L-1", 0, HOY.minusDays(1)));

        assertThat(servicio.listarPorVencer(30)).isEmpty();
    }

    @Test
    void porVencerConDiasFueraDeRangoLanzaSolicitudInvalida() {
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.listarPorVencer(0))
                .withMessage("El parámetro dias debe ser un entero entre 1 y 365.");
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.listarPorVencer(366));
    }

    @Test
    void vencidosSoloIncluyeFechasAnterioresAHoy() {
        Medicamento viejo = registrar("Dolex", "L-1", 1, 0, HOY.plusDays(1));
        Medicamento masViejo = registrar("Dolex", "L-2", 1, 0, HOY.plusDays(1));
        registrar("Dolex", "L-3", 1, 0, HOY);
        servicio.editarMedicamento(viejo.getIdMedicamento(), datos("Dolex", "L-1", 0, HOY.minusDays(1)));
        servicio.editarMedicamento(masViejo.getIdMedicamento(), datos("Dolex", "L-2", 0, HOY.minusDays(40)));

        assertThat(servicio.listarVencidos()).extracting(m -> m.getDatos().lote())
                .containsExactly("L-2", "L-1");
    }

    /**
     * "Hoy" es el día de la zona horaria del reloj, no el de UTC: a las 22:00 de
     * Bogotá ya es el día siguiente en UTC, pero lo que vence hoy no está vencido.
     */
    @Test
    void hoyEsElDiaDeLaZonaHorariaDelRelojNoElDeUtc() {
        Instant nocheEnBogota = Instant.parse("2026-06-16T03:00:00Z");
        MedicamentoService enBogota = new MedicamentoService(repositorio,
                Clock.fixed(nocheEnBogota, ZoneId.of("America/Bogota")));
        Medicamento venceHoy = enBogota.registrarMedicamento(datos("Dolex", "L-1", 0, HOY), 1);

        assertThat(enBogota.listarVencidos()).isEmpty();
        assertThat(enBogota.listarPorVencer(1)).containsExactly(venceHoy);

        MedicamentoService enUtc = new MedicamentoService(repositorio, Clock.fixed(nocheEnBogota, ZoneOffset.UTC));
        assertThat(enUtc.listarVencidos()).containsExactly(venceHoy);
    }
}
