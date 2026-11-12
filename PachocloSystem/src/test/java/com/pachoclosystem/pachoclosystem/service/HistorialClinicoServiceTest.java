package com.pachoclosystem.pachoclosystem.service;

import com.pachoclosystem.pachoclosystem.client.MedicamentosClient;
import com.pachoclosystem.pachoclosystem.dto.RegistroResponse;
import com.pachoclosystem.pachoclosystem.dto.SignosVitalesRequest;
import com.pachoclosystem.pachoclosystem.exception.NotFoundException;
import com.pachoclosystem.pachoclosystem.exception.SolicitudInvalidaException;
import com.pachoclosystem.pachoclosystem.model.Enfermero;
import com.pachoclosystem.pachoclosystem.model.NivelExperiencia;
import com.pachoclosystem.pachoclosystem.model.Paciente;
import com.pachoclosystem.pachoclosystem.model.TipoRegistro;
import com.pachoclosystem.pachoclosystem.model.TrabajadorHospital;
import com.pachoclosystem.pachoclosystem.repository.PacienteRepositoryEnMemoria;
import com.pachoclosystem.pachoclosystem.repository.RegistroClinicoRepositoryEnMemoria;
import com.pachoclosystem.pachoclosystem.repository.TrabajadorRepositoryEnMemoria;
import com.pachoclosystem.pachoclosystem.repository.UsuarioRepositoryEnMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

/** Reglas de negocio del historial clínico, sin contexto Spring. */
class HistorialClinicoServiceTest {

    private PacienteRepositoryEnMemoria repositorioPacientes;
    private TrabajadorRepositoryEnMemoria repositorioTrabajadores;
    private RegistroClinicoRepositoryEnMemoria repositorioRegistros;
    private PacienteService servicioPacientes;
    private TrabajadorService servicioTrabajadores;
    private HistorialClinicoService servicio;

    @BeforeEach
    void preparar() {
        repositorioPacientes = new PacienteRepositoryEnMemoria();
        repositorioTrabajadores = new TrabajadorRepositoryEnMemoria();
        servicioPacientes = new PacienteService(repositorioPacientes);
        servicioTrabajadores = new TrabajadorService(repositorioTrabajadores, new UsuarioRepositoryEnMemoria());
        repositorioRegistros = new RegistroClinicoRepositoryEnMemoria(repositorioPacientes);
        servicio = new HistorialClinicoService(repositorioPacientes, repositorioTrabajadores, repositorioRegistros);
    }

    private Paciente paciente(String nombre) {
        return servicioPacientes.registrarPaciente(nombre, 30, 101);
    }

    private TrabajadorHospital doctor(String nombre) {
        return servicioTrabajadores.registrarTrabajador(nombre, "Doctor", "Cardiologia", null);
    }

    private Enfermero enfermero(String nombre) {
        return (Enfermero) servicioTrabajadores.registrarTrabajador(
                nombre, "Enfermero", null, NivelExperiencia.AVANZADO);
    }

    /** Espera para que dos registros consecutivos tengan fechas distintas (resolución en ms). */
    private static void dormir(long milisegundos) {
        try {
            Thread.sleep(milisegundos);
        } catch (InterruptedException excepcion) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(excepcion);
        }
    }

    /**
     * La fecha del registro es la hora local de la zona del reloj de la
     * aplicación, no la del servidor: a las 03:00 UTC son las 22:00 del día
     * anterior en Bogotá. Se trunca a milisegundos porque es la precisión con la
     * que MongoDB guarda las fechas (BSON Date).
     */
    @Test
    void laFechaDelRegistroEsLaHoraLocalDeLaZonaDelReloj() {
        Clock bogota = Clock.fixed(Instant.parse("2026-06-16T03:00:00.123456789Z"), ZoneId.of("America/Bogota"));
        HistorialClinicoService enBogota = new HistorialClinicoService(repositorioPacientes,
                repositorioTrabajadores, repositorioRegistros, null, TransactionOperations.withoutTransaction(),
                bogota);
        Paciente p = paciente("Ana Torres");
        TrabajadorHospital d = doctor("Carlos Mena");

        RegistroResponse registro = enBogota.agregarRegistroPaciente(p.getIdPaciente(), d.getIdTrabajador(),
                TipoRegistro.EVOLUCION, "Paciente estable", null);

        assertThat(registro.fecha()).isEqualTo(LocalDateTime.of(2026, 6, 15, 22, 0, 0, 123_000_000));
    }

    @Test
    void agregaDiagnosticoAlHistorialDelPaciente() {
        Paciente p = paciente("Ana Torres");
        TrabajadorHospital d = doctor("Carlos Mena");

        RegistroResponse registro = servicio.agregarRegistroPaciente(
                p.getIdPaciente(), d.getIdTrabajador(), TipoRegistro.DIAGNOSTICO,
                "Hipertension leve", null);

        assertThat(registro.idRegistro()).isNotBlank();
        assertThat(registro.idPaciente()).isEqualTo(p.getIdPaciente());
        assertThat(registro.nombrePaciente()).isEqualTo("Ana Torres");
        assertThat(registro.fecha()).isNotNull();
        assertThat(registro.tipo()).isEqualTo(TipoRegistro.DIAGNOSTICO);
        assertThat(registro.contenido()).isEqualTo("Hipertension leve");
        assertThat(registro.autor().idTrabajador()).isEqualTo(d.getIdTrabajador());
        assertThat(registro.autor().nombreCompleto()).isEqualTo("Carlos Mena");
        assertThat(registro.autor().rol()).isEqualTo("Doctor");
        assertThat(repositorioRegistros.listarPorPaciente(p.getIdPaciente())).hasSize(1);
    }

    @Test
    void agregaSignosVitalesFormateandoElContenido() {
        Paciente p = paciente("Ana Torres");
        Enfermero e = enfermero("Maria Lopez");
        SignosVitalesRequest signos = new SignosVitalesRequest(36.5, 80, 120, 80, 16, 98, null);

        RegistroResponse registro = servicio.agregarRegistroPaciente(
                p.getIdPaciente(), e.getIdTrabajador(), TipoRegistro.SIGNOS_VITALES, null, signos);

        assertThat(registro.contenido()).isEqualTo(
                "Signos vitales - Temp: 36.5°C | FC: 80 lpm | PA: 120/80 mmHg"
                        + " | FR: 16 rpm | SpO2: 98%");
        assertThat(registro.tipo()).isEqualTo(TipoRegistro.SIGNOS_VITALES);
    }

    @Test
    void agregaSignosVitalesConObservacionesRecortadas() {
        Paciente p = paciente("Ana Torres");
        Enfermero e = enfermero("Maria Lopez");
        SignosVitalesRequest signos = new SignosVitalesRequest(37.2, 72, 118, 76, 15, 97,
                "  paciente estable  ");

        RegistroResponse registro = servicio.agregarRegistroPaciente(
                p.getIdPaciente(), e.getIdTrabajador(), TipoRegistro.SIGNOS_VITALES, null, signos);

        assertThat(registro.contenido()).isEqualTo(
                "Signos vitales - Temp: 37.2°C | FC: 72 lpm | PA: 118/76 mmHg"
                        + " | FR: 15 rpm | SpO2: 97% | Obs: paciente estable");
    }

    @Test
    void signosVitalesSonObligatoriosParaEseTipoDeRegistro() {
        Paciente p = paciente("Ana Torres");
        Enfermero e = enfermero("Maria Lopez");

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.agregarRegistroPaciente(
                        p.getIdPaciente(), e.getIdTrabajador(), TipoRegistro.SIGNOS_VITALES, null, null))
                .withMessage("Los signos vitales son obligatorios para este tipo de registro.");
    }

    @Test
    void laPresionDiastolicaDebeSerMenorQueLaSistolica() {
        Paciente p = paciente("Ana Torres");
        Enfermero e = enfermero("Maria Lopez");
        SignosVitalesRequest signos = new SignosVitalesRequest(36.5, 80, 100, 100, 16, 98, null);

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.agregarRegistroPaciente(
                        p.getIdPaciente(), e.getIdTrabajador(), TipoRegistro.SIGNOS_VITALES, null, signos))
                .withMessage("La presión diastólica debe ser menor que la sistólica.");
    }

    @Test
    void lasObservacionesNoPuedenSerSoloNumeros() {
        Paciente p = paciente("Ana Torres");
        Enfermero e = enfermero("Maria Lopez");
        SignosVitalesRequest signos = new SignosVitalesRequest(36.5, 80, 120, 80, 16, 98, "1234");

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.agregarRegistroPaciente(
                        p.getIdPaciente(), e.getIdTrabajador(), TipoRegistro.SIGNOS_VITALES, null, signos))
                .withMessage("Las observaciones no pueden ser solo números.");
    }

    @Test
    void losErroresDeSignosVitalesSeAcumulanEnOrden() {
        Paciente p = paciente("Ana Torres");
        Enfermero e = enfermero("Maria Lopez");
        SignosVitalesRequest signos = new SignosVitalesRequest(36.5, 80, 100, 120, 16, 98, "1234");

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.agregarRegistroPaciente(
                        p.getIdPaciente(), e.getIdTrabajador(), TipoRegistro.SIGNOS_VITALES, null, signos))
                .satisfies(excepcion -> assertThat(excepcion.getErrores()).containsExactly(
                        "La presión diastólica debe ser menor que la sistólica.",
                        "Las observaciones no pueden ser solo números."));
    }

    @Test
    void elContenidoNoPuedeEstarVacio() {
        Paciente p = paciente("Ana Torres");
        TrabajadorHospital d = doctor("Carlos Mena");

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.agregarRegistroPaciente(
                        p.getIdPaciente(), d.getIdTrabajador(), TipoRegistro.EVOLUCION, "   ", null))
                .withMessage("El contenido no puede estar vacío.");
    }

    @Test
    void elContenidoNoPuedeSerDemasiadoCorto() {
        Paciente p = paciente("Ana Torres");
        TrabajadorHospital d = doctor("Carlos Mena");

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.agregarRegistroPaciente(
                        p.getIdPaciente(), d.getIdTrabajador(), TipoRegistro.EVOLUCION, "abc", null))
                .withMessage("El contenido es demasiado corto (mínimo 5 caracteres).");
    }

    @Test
    void elContenidoDebeIncluirTextoYNoSoloNumeros() {
        Paciente p = paciente("Ana Torres");
        TrabajadorHospital d = doctor("Carlos Mena");

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.agregarRegistroPaciente(
                        p.getIdPaciente(), d.getIdTrabajador(), TipoRegistro.EVOLUCION,
                        "12345678", null))
                .withMessage("El contenido debe incluir texto descriptivo, no solo números.");
    }

    @Test
    void elContenidoSeRecortaAntesDeValidarlo() {
        Paciente p = paciente("Ana Torres");
        TrabajadorHospital d = doctor("Carlos Mena");

        RegistroResponse registro = servicio.agregarRegistroPaciente(
                p.getIdPaciente(), d.getIdTrabajador(), TipoRegistro.EVOLUCION,
                "   Evolucion favorable   ", null);

        assertThat(registro.contenido()).isEqualTo("Evolucion favorable");
    }

    @Test
    void agregarRegistroAPacienteInexistenteLanzaNotFound() {
        TrabajadorHospital d = doctor("Carlos Mena");

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.agregarRegistroPaciente(
                        "PAC-9999", d.getIdTrabajador(), TipoRegistro.DIAGNOSTICO,
                        "Hipertension leve", null))
                .withMessage("No se encontró el paciente PAC-9999.");
    }

    @Test
    void agregarRegistroConAutorInexistenteLanzaNotFound() {
        Paciente p = paciente("Ana Torres");

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.agregarRegistroPaciente(
                        p.getIdPaciente(), "DOC-9999", TipoRegistro.DIAGNOSTICO,
                        "Hipertension leve", null))
                .withMessage("No se encontró un trabajador con el ID DOC-9999.");
    }

    @Test
    void historialDePacienteInexistenteLanzaNotFound() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.obtenerRegistrosPorPaciente("PAC-9999", null))
                .withMessage("No se encontró el paciente PAC-9999.");
    }

    @Test
    void historialDePacienteDadoDeBajaLanzaNotFoundYNoAdmiteNuevosRegistros() {
        Paciente p = paciente("Ana Torres");
        TrabajadorHospital d = doctor("Carlos Mena");
        servicio.agregarRegistroPaciente(p.getIdPaciente(), d.getIdTrabajador(),
                TipoRegistro.DIAGNOSTICO, "Hipertension leve", null);
        servicioPacientes.eliminarPaciente(p.getIdPaciente());

        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.obtenerRegistrosPorPaciente(p.getIdPaciente(), null))
                .withMessage("No se encontró el paciente " + p.getIdPaciente() + ".");
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.agregarRegistroPaciente(p.getIdPaciente(), d.getIdTrabajador(),
                        TipoRegistro.DIAGNOSTICO, "Otra observacion", null))
                .withMessage("No se encontró el paciente " + p.getIdPaciente() + ".");
    }

    @Test
    void darDeBajaAlPacienteDuranteLaSalidaDeStockNoLoVuelveACrear() {
        Paciente p = paciente("Ana Torres");
        TrabajadorHospital d = doctor("Carlos Mena");
        MedicamentosClient cliente = mock(MedicamentosClient.class);
        // Mientras se espera la salida de stock, un ADMIN da de baja al paciente.
        doAnswer(invocacion -> {
            servicioPacientes.eliminarPaciente(p.getIdPaciente());
            return null;
        }).when(cliente).registrarSalida(anyString(), anyInt());
        HistorialClinicoService conStock = new HistorialClinicoService(
                repositorioPacientes, repositorioTrabajadores, repositorioRegistros, cliente);

        conStock.agregarRegistroPaciente(p.getIdPaciente(), d.getIdTrabajador(), TipoRegistro.MEDICACION,
                "Paracetamol 500 mg via oral", null, "MED-0001", 2);

        assertThat(servicioPacientes.listarPacientes(null)).isEmpty();
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicioPacientes.obtenerPaciente(p.getIdPaciente()));
    }

    @Test
    void elHistorialGlobalExcluyeLosRegistrosDePacientesDadosDeBaja() {
        Paciente activo = paciente("Ana Torres");
        Paciente dadoDeBaja = paciente("Bruno Diaz");
        TrabajadorHospital d = doctor("Carlos Mena");
        servicio.agregarRegistroPaciente(activo.getIdPaciente(), d.getIdTrabajador(),
                TipoRegistro.DIAGNOSTICO, "Diagnostico de Ana", null);
        servicio.agregarRegistroPaciente(dadoDeBaja.getIdPaciente(), d.getIdTrabajador(),
                TipoRegistro.EVOLUCION, "Evolucion de Bruno", null);
        servicioPacientes.eliminarPaciente(dadoDeBaja.getIdPaciente());

        List<RegistroResponse> historial = servicio.obtenerTodosLosRegistros("todos", null);
        assertThat(historial).hasSize(1);
        assertThat(historial.get(0).idPaciente()).isEqualTo(activo.getIdPaciente());
    }

    @Test
    void elFiltroDelHistorialGeneralDebeSerTodosPacienteOAutor() {
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.obtenerTodosLosRegistros("inventado", null))
                .withMessage("El filtro debe ser todos, paciente o autor.");
    }

    @Test
    void elHistorialGeneralSeOrdenaPorFecha() {
        Paciente a = paciente("Ana Torres");
        Paciente b = paciente("Bruno Diaz");
        TrabajadorHospital d = doctor("Carlos Mena");
        TrabajadorHospital e = enfermero("Maria Lopez");

        // El registro de Bruno se crea primero: si no se ordenara por fecha,
        // el de Ana (paciente insertado antes) aparecería en primer lugar.
        RegistroResponse registroB = servicio.agregarRegistroPaciente(
                b.getIdPaciente(), e.getIdTrabajador(), TipoRegistro.EVOLUCION,
                "Evolucion de Bruno", null);
        // LocalDateTime.now() tiene resolución de milisegundos: se separan ambas
        // fechas para que el orden cronológico sea comprobable de forma estable.
        dormir(20);
        RegistroResponse registroA = servicio.agregarRegistroPaciente(
                a.getIdPaciente(), d.getIdTrabajador(), TipoRegistro.DIAGNOSTICO,
                "Diagnostico de Ana", null);

        List<RegistroResponse> historial = servicio.obtenerTodosLosRegistros("todos", null);

        assertThat(historial).extracting(RegistroResponse::idRegistro)
                .containsExactly(registroB.idRegistro(), registroA.idRegistro());
    }

    @Test
    void elHistorialGeneralSeFiltraPorPacientePorAutorOporAmbos() {
        Paciente a = paciente("Ana Torres");
        Paciente b = paciente("Bruno Diaz");
        TrabajadorHospital d = doctor("Carlos Mena");
        TrabajadorHospital e = enfermero("Maria Lopez");
        servicio.agregarRegistroPaciente(a.getIdPaciente(), d.getIdTrabajador(),
                TipoRegistro.DIAGNOSTICO, "Diagnostico de Ana", null);
        servicio.agregarRegistroPaciente(b.getIdPaciente(), e.getIdTrabajador(),
                TipoRegistro.EVOLUCION, "Evolucion de Bruno", null);

        List<RegistroResponse> porPaciente = servicio.obtenerTodosLosRegistros("paciente", "ana");
        assertThat(porPaciente).hasSize(1);
        assertThat(porPaciente.get(0).idPaciente()).isEqualTo(a.getIdPaciente());

        List<RegistroResponse> porAutor = servicio.obtenerTodosLosRegistros("AUTOR", "maria");
        assertThat(porAutor).hasSize(1);
        assertThat(porAutor.get(0).idPaciente()).isEqualTo(b.getIdPaciente());

        List<RegistroResponse> porIdDeAutor = servicio.obtenerTodosLosRegistros(null, "doc-");
        assertThat(porIdDeAutor).hasSize(1);
        assertThat(porIdDeAutor.get(0).idPaciente()).isEqualTo(a.getIdPaciente());

        List<RegistroResponse> sinCoincidencias = servicio.obtenerTodosLosRegistros(" ", "zzz");
        assertThat(sinCoincidencias).isEmpty();
    }

    @Test
    void elHistorialDeUnPacienteSeFiltraPorAutor() {
        Paciente a = paciente("Ana Torres");
        TrabajadorHospital d = doctor("Carlos Mena");
        TrabajadorHospital e = enfermero("Maria Lopez");
        servicio.agregarRegistroPaciente(a.getIdPaciente(), d.getIdTrabajador(),
                TipoRegistro.DIAGNOSTICO, "Diagnostico de Ana", null);
        servicio.agregarRegistroPaciente(a.getIdPaciente(), e.getIdTrabajador(),
                TipoRegistro.EVOLUCION, "Evolucion de Ana", null);

        List<RegistroResponse> filtrados = servicio.obtenerRegistrosPorPaciente(
                a.getIdPaciente(), "maria");

        assertThat(filtrados).hasSize(1);
        assertThat(filtrados.get(0).contenido()).isEqualTo("Evolucion de Ana");
    }

    @Test
    void eliminarAlAutorNoBorraLosRegistrosYaEscritos() {
        Paciente p = paciente("Ana Torres");
        Enfermero e = enfermero("Maria Lopez");
        servicio.agregarRegistroPaciente(p.getIdPaciente(), e.getIdTrabajador(),
                TipoRegistro.EVOLUCION, "Evolucion de Ana", null);

        servicioTrabajadores.eliminarTrabajador(e.getIdTrabajador());

        assertThat(repositorioTrabajadores.buscarPorId(e.getIdTrabajador())).isNull();
        List<RegistroResponse> registros = servicio.obtenerRegistrosPorPaciente(
                p.getIdPaciente(), null);
        assertThat(registros).hasSize(1);
        assertThat(registros.get(0).autor().idTrabajador()).isEqualTo(e.getIdTrabajador());
        assertThat(registros.get(0).autor().nombreCompleto()).isEqualTo("Maria Lopez");
        assertThat(registros.get(0).autor().rol()).isEqualTo("Enfermero");
    }
}
