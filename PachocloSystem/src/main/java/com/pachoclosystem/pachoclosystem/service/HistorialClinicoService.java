package com.pachoclosystem.pachoclosystem.service;

import com.pachoclosystem.pachoclosystem.client.MedicamentosClient;
import com.pachoclosystem.pachoclosystem.dto.RegistroResponse;
import com.pachoclosystem.pachoclosystem.dto.SignosVitalesRequest;
import com.pachoclosystem.pachoclosystem.exception.AccesoDenegadoException;
import com.pachoclosystem.pachoclosystem.exception.NotFoundException;
import com.pachoclosystem.pachoclosystem.exception.SolicitudInvalidaException;
import com.pachoclosystem.pachoclosystem.model.Paciente;
import com.pachoclosystem.pachoclosystem.model.RegistroClinico;
import com.pachoclosystem.pachoclosystem.model.Rol;
import com.pachoclosystem.pachoclosystem.model.TipoRegistro;
import com.pachoclosystem.pachoclosystem.model.TrabajadorHospital;
import com.pachoclosystem.pachoclosystem.model.Usuario;
import com.pachoclosystem.pachoclosystem.repository.IPacienteRepository;
import com.pachoclosystem.pachoclosystem.repository.IRegistroClinicoRepository;
import com.pachoclosystem.pachoclosystem.repository.ITrabajadoresRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

@Service
public class HistorialClinicoService {

    /**
     * Tipos de registro que puede crear cada rol. El ADMIN no está: no tiene
     * trabajador vinculado y no puede firmar registros.
     */
    private static final Map<Rol, Set<TipoRegistro>> TIPOS_POR_ROL = Map.of(
            Rol.DOCTOR, EnumSet.allOf(TipoRegistro.class),
            Rol.ENFERMERO, EnumSet.of(TipoRegistro.EVOLUCION, TipoRegistro.SIGNOS_VITALES,
                    TipoRegistro.MEDICACION));

    private static final Pattern PATRON_CONTIENE_TEXTO = Pattern.compile(".*[A-Za-zÁÉÍÓÚÑÜáéíóúñü].*");

    private final IPacienteRepository repositorioPacientes;
    private final ITrabajadoresRepository repositorioTrabajadores;
    private final IRegistroClinicoRepository repositorioRegistros;
    /** Transacción corta en la que se guarda el registro (sin transacción en los tests unitarios). */
    private final TransactionOperations transacciones;
    /** Fecha de los registros: el reloj de la aplicación, en su zona horaria oficial. */
    private final Clock reloj;
    /** Para descontar stock en registros de MEDICACION; null en tests unitarios sin Spring. */
    private final MedicamentosClient clienteMedicamentos;

    public HistorialClinicoService(IPacienteRepository repositorioPacientes,
                                   ITrabajadoresRepository repositorioTrabajadores,
                                   IRegistroClinicoRepository repositorioRegistros) {
        this(repositorioPacientes, repositorioTrabajadores, repositorioRegistros, null);
    }

    /** Sin Spring (tests unitarios): sin transacción y con el reloj del sistema. */
    public HistorialClinicoService(IPacienteRepository repositorioPacientes,
                                   ITrabajadoresRepository repositorioTrabajadores,
                                   IRegistroClinicoRepository repositorioRegistros,
                                   MedicamentosClient clienteMedicamentos) {
        this(repositorioPacientes, repositorioTrabajadores, repositorioRegistros, clienteMedicamentos,
                TransactionOperations.withoutTransaction(), Clock.systemDefaultZone());
    }

    @Autowired
    public HistorialClinicoService(IPacienteRepository repositorioPacientes,
                                   ITrabajadoresRepository repositorioTrabajadores,
                                   IRegistroClinicoRepository repositorioRegistros,
                                   MedicamentosClient clienteMedicamentos,
                                   TransactionOperations transacciones,
                                   Clock reloj) {
        this.repositorioPacientes = repositorioPacientes;
        this.repositorioTrabajadores = repositorioTrabajadores;
        this.repositorioRegistros = repositorioRegistros;
        this.clienteMedicamentos = clienteMedicamentos;
        this.transacciones = transacciones;
        this.reloj = reloj;
    }

    /**
     * Crea un registro firmado por el usuario autenticado: el autor es su
     * trabajador vinculado, nunca un dato de la petición ni de los claims.
     *
     * <ul>
     *   <li>Sin trabajador vinculado (el ADMIN): 403.</li>
     *   <li>{@code idAutorDeclarado} es opcional; si viene y no es su
     *       trabajador: 400.</li>
     *   <li>El rol limita el tipo de registro ({@link #TIPOS_POR_ROL}): 403.</li>
     * </ul>
     *
     * <p>El descuento de stock de MEDICACION lo hace este servicio con el
     * cliente interno de MedicamentosService (una llamada saliente que no pasa
     * por la cadena de seguridad de esta API), así que lo puede pedir cualquiera
     * que pueda crear el registro, aunque no tenga acceso a las rutas de salidas
     * de stock.</p>
     */
    public RegistroResponse agregarRegistroComo(Usuario usuario, String idPaciente, String idAutorDeclarado,
                                                TipoRegistro tipo, String contenido,
                                                SignosVitalesRequest signos, String idMedicamento,
                                                Integer cantidad) {
        return agregarRegistroComo(usuario, idPaciente, idAutorDeclarado, tipo, contenido, signos,
                idMedicamento, cantidad, null);
    }

    /**
     * Como {@link #agregarRegistroComo(Usuario, String, String, TipoRegistro, String,
     * SignosVitalesRequest, String, Integer)}, pero si hay descuento de stock la
     * salida se pide con {@code claveIdempotencia} (ver
     * {@link MedicamentosClient#registrarSalida(String, int, String)}). Clave nula =
     * la salida de siempre.
     */
    public RegistroResponse agregarRegistroComo(Usuario usuario, String idPaciente, String idAutorDeclarado,
                                                TipoRegistro tipo, String contenido,
                                                SignosVitalesRequest signos, String idMedicamento,
                                                Integer cantidad, String claveIdempotencia) {
        return agregarRegistroComo(usuario, idPaciente, idAutorDeclarado, tipo, contenido, signos,
                idMedicamento, cantidad, claveIdempotencia, null);
    }

    /**
     * Como el anterior, y además ejecuta {@code enLaMismaTransaccion} con el
     * registro creado dentro de la transacción en la que se guarda: si esa acción
     * falla, el registro tampoco queda guardado. Lo usa la idempotencia para
     * guardar su clave a la vez que el registro.
     */
    public RegistroResponse agregarRegistroComo(Usuario usuario, String idPaciente, String idAutorDeclarado,
                                                TipoRegistro tipo, String contenido,
                                                SignosVitalesRequest signos, String idMedicamento,
                                                Integer cantidad, String claveIdempotencia,
                                                Consumer<RegistroResponse> enLaMismaTransaccion) {
        String idAutor = usuario.getIdTrabajador();
        if (idAutor == null) {
            throw new AccesoDenegadoException(
                    "Solo un usuario vinculado a un trabajador puede crear registros.");
        }
        if (idAutorDeclarado != null && !idAutorDeclarado.isBlank()
                && !idAutorDeclarado.trim().equals(idAutor)) {
            throw new SolicitudInvalidaException("El idAutor enviado no coincide con el trabajador de su "
                    + "usuario: el autor de un registro es siempre quien inicia sesión.");
        }
        if (tipo != null && !TIPOS_POR_ROL.getOrDefault(usuario.getRol(), Set.of()).contains(tipo)) {
            throw new AccesoDenegadoException("Su rol no puede crear registros de tipo " + tipo + ".");
        }
        return agregarRegistroPaciente(idPaciente, idAutor, tipo, contenido, signos, idMedicamento, cantidad,
                claveIdempotencia, enLaMismaTransaccion);
    }

    public RegistroResponse agregarRegistroPaciente(String idPaciente, String idAutor, TipoRegistro tipo,
                                                    String contenido, SignosVitalesRequest signos) {
        return agregarRegistroPaciente(idPaciente, idAutor, tipo, contenido, signos, null, null);
    }

    /**
     * Agrega un registro. Si es MEDICACION con {@code idMedicamento} y {@code cantidad},
     * primero se hace la salida de stock en MedicamentosService y solo si sale bien se
     * guarda el registro: si la salida falla (medicamento inexistente, stock
     * insuficiente, vencido o servicio caído) no se crea nada.
     */
    public RegistroResponse agregarRegistroPaciente(String idPaciente, String idAutor, TipoRegistro tipo,
                                                    String contenido, SignosVitalesRequest signos,
                                                    String idMedicamento, Integer cantidad) {
        return agregarRegistroPaciente(idPaciente, idAutor, tipo, contenido, signos, idMedicamento, cantidad,
                null, null);
    }

    /**
     * Con {@code claveIdempotencia}, la salida de stock se pide con esa clave y se
     * reintenta una vez si no responde; sin ella (null), la salida de siempre.
     *
     * <p>La salida de stock se pide <strong>fuera</strong> de cualquier
     * transacción (no se retiene una conexión durante la llamada remota); después,
     * el registro se guarda en una transacción corta. Si el paciente se da de baja
     * entre medias, el registro se añade igualmente y el paciente sigue de baja.</p>
     */
    private RegistroResponse agregarRegistroPaciente(String idPaciente, String idAutor, TipoRegistro tipo,
                                                     String contenido, SignosVitalesRequest signos,
                                                     String idMedicamento, Integer cantidad,
                                                     String claveIdempotencia,
                                                     Consumer<RegistroResponse> enLaMismaTransaccion) {
        validarMedicacion(tipo, idMedicamento, cantidad);
        String contenidoFinal = construirContenido(tipo, contenido, signos);

        Paciente paciente = buscarPacienteActivo(idPaciente);
        TrabajadorHospital autor = repositorioTrabajadores.buscarPorId(idAutor);
        if (autor == null) {
            throw new NotFoundException("No se encontró un trabajador con el ID " + idAutor + ".");
        }

        String medicamento = idMedicamento == null ? null : idMedicamento.trim();
        if (medicamento != null) {
            if (clienteMedicamentos == null) {
                throw new IllegalStateException("No hay cliente de medicamentos configurado.");
            }
            if (claveIdempotencia == null) {
                clienteMedicamentos.registrarSalida(medicamento, cantidad);
            } else {
                clienteMedicamentos.registrarSalida(medicamento, cantidad, claveIdempotencia);
            }
        }

        RegistroClinico registro = new RegistroClinico(paciente.getIdPaciente(), tipo, contenidoFinal, autor,
                medicamento, cantidad, reloj);
        return TransaccionConReintentos.ejecutar(transacciones, () -> {
            repositorioRegistros.insertar(paciente.getIdPaciente(), registro);
            RegistroResponse creado = RegistroResponse.from(paciente, registro);
            if (enLaMismaTransaccion != null) {
                enLaMismaTransaccion.accept(creado);
            }
            return creado;
        });
    }

    /** Todos los registros de todos los pacientes, ordenados por fecha. filtro: todos | paciente | autor. */
    public List<RegistroResponse> obtenerTodosLosRegistros(String filtro, String texto) {
        String modo = filtro == null || filtro.isBlank() ? "todos" : filtro.trim().toLowerCase(Locale.ROOT);
        if (!List.of("todos", "paciente", "autor").contains(modo)) {
            throw new SolicitudInvalidaException("El filtro debe ser todos, paciente o autor.");
        }

        // El historial global excluye los registros de pacientes dados de baja.
        List<RegistroResponse> resultado = new ArrayList<>();
        for (IRegistroClinicoRepository.RegistroDePaciente fila : repositorioRegistros.listarDePacientesActivos()) {
            resultado.add(RegistroResponse.from(fila.idPaciente(), fila.nombrePaciente(), fila.registro()));
        }
        resultado.sort(Comparator.comparing(RegistroResponse::fecha));
        return aplicarFiltro(resultado, modo, texto);
    }

    /** Registros de un paciente; si hay texto, filtra por autor (como la vista de un paciente). */
    public List<RegistroResponse> obtenerRegistrosPorPaciente(String idPaciente, String texto) {
        Paciente paciente = buscarPacienteActivo(idPaciente);
        List<RegistroResponse> registros = repositorioRegistros.listarPorPaciente(idPaciente).stream()
                .map(r -> RegistroResponse.from(paciente, r))
                .toList();
        return aplicarFiltro(registros, "autor", texto);
    }

    /** Devuelve el paciente si existe y está activo; si no, 404 (un paciente dado de baja es inexistente). */
    private Paciente buscarPacienteActivo(String idPaciente) {
        Paciente paciente = repositorioPacientes.buscarPorId(idPaciente);
        if (paciente == null || !paciente.isActivo()) {
            throw new NotFoundException("No se encontró el paciente " + idPaciente + ".");
        }
        return paciente;
    }

    private List<RegistroResponse> aplicarFiltro(List<RegistroResponse> registros, String modo, String texto) {
        String filtro = texto == null ? "" : texto.trim().toLowerCase(Locale.ROOT);
        if (filtro.isEmpty()) {
            return registros;
        }
        return registros.stream().filter(rc -> {
            boolean coincidePaciente = rc.nombrePaciente().toLowerCase(Locale.ROOT).contains(filtro)
                    || rc.idPaciente().toLowerCase(Locale.ROOT).contains(filtro);
            boolean coincideAutor = rc.autor().nombreCompleto().toLowerCase(Locale.ROOT).contains(filtro)
                    || rc.autor().idTrabajador().toLowerCase(Locale.ROOT).contains(filtro);
            return switch (modo) {
                case "paciente" -> coincidePaciente;
                case "autor" -> coincideAutor;
                default -> coincidePaciente || coincideAutor;
            };
        }).toList();
    }

    /** idMedicamento y cantidad: los dos o ninguno, y solo en registros de MEDICACION. */
    private void validarMedicacion(TipoRegistro tipo, String idMedicamento, Integer cantidad) {
        boolean hayMedicamento = idMedicamento != null && !idMedicamento.isBlank();
        boolean hayCantidad = cantidad != null;
        if (!hayMedicamento && !hayCantidad) {
            return;
        }
        if (tipo != TipoRegistro.MEDICACION) {
            throw new SolicitudInvalidaException(
                    "Solo los registros de MEDICACION pueden descontar stock de un medicamento.");
        }
        if (hayMedicamento != hayCantidad) {
            throw new SolicitudInvalidaException(
                    "Para descontar stock hay que indicar el ID del medicamento y la cantidad.");
        }
    }

    private String construirContenido(TipoRegistro tipo, String contenido, SignosVitalesRequest signos) {
        List<String> errores = new ArrayList<>();

        if (tipo == TipoRegistro.SIGNOS_VITALES) {
            if (signos == null) {
                throw new SolicitudInvalidaException("Los signos vitales son obligatorios para este tipo de registro.");
            }
            if (signos.presionDiastolica() >= signos.presionSistolica()) {
                errores.add("La presión diastólica debe ser menor que la sistólica.");
            }
            String observaciones = signos.observaciones() == null ? "" : signos.observaciones().trim();
            if (!observaciones.isEmpty() && !PATRON_CONTIENE_TEXTO.matcher(observaciones).matches()) {
                errores.add("Las observaciones no pueden ser solo números.");
            }
            if (!errores.isEmpty()) {
                throw new SolicitudInvalidaException(errores);
            }
            String texto = String.format(Locale.US,
                    "Signos vitales - Temp: %.1f°C | FC: %d lpm | PA: %d/%d mmHg | FR: %d rpm | SpO2: %d%%",
                    signos.temperatura(), signos.frecCardiaca(), signos.presionSistolica(),
                    signos.presionDiastolica(), signos.frecRespiratoria(), signos.saturacion());
            return observaciones.isEmpty() ? texto : texto + " | Obs: " + observaciones;
        }

        String texto = contenido == null ? "" : contenido.trim();
        if (texto.isEmpty()) {
            errores.add("El contenido no puede estar vacío.");
        } else if (texto.length() < 5) {
            errores.add("El contenido es demasiado corto (mínimo 5 caracteres).");
        } else if (!PATRON_CONTIENE_TEXTO.matcher(texto).matches()) {
            errores.add("El contenido debe incluir texto descriptivo, no solo números.");
        }
        if (!errores.isEmpty()) {
            throw new SolicitudInvalidaException(errores);
        }
        return texto;
    }
}
