package com.pachoclosystem.pachoclosystem.controller;

import com.pachoclosystem.pachoclosystem.dto.ErrorResponse;
import com.pachoclosystem.pachoclosystem.dto.RegistroRequest;
import com.pachoclosystem.pachoclosystem.dto.RegistroResponse;
import com.pachoclosystem.pachoclosystem.model.Usuario;
import com.pachoclosystem.pachoclosystem.service.HistorialClinicoService;
import com.pachoclosystem.pachoclosystem.service.HistorialIdempotenteService;
import com.pachoclosystem.pachoclosystem.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@Tag(name = "Historial clínico", description = """
        Registros firmados por el trabajador del usuario autenticado. El historial global
        excluye a los pacientes dados de baja.
        """)
public class HistorialClinicoController {

    /** Clave opcional para que repetir la creación de un registro no cree otro. */
    public static final String CABECERA_IDEMPOTENCIA = "Idempotency-Key";
    /** En la respuesta: {@code true} si es la repetición de un registro ya creado con esa clave. */
    public static final String CABECERA_REPETIDA = "Idempotency-Replayed";

    private final HistorialClinicoService servicio;
    private final HistorialIdempotenteService historialIdempotente;
    private final UsuarioService usuarioService;

    public HistorialClinicoController(HistorialClinicoService servicio,
                                      HistorialIdempotenteService historialIdempotente,
                                      UsuarioService usuarioService) {
        this.servicio = servicio;
        this.historialIdempotente = historialIdempotente;
        this.usuarioService = usuarioService;
    }

    @Operation(summary = "Listar el historial de todos los pacientes",
            description = """
                    Devuelve los registros de los pacientes activos, del más reciente al más antiguo.
                    `filtro` decide en qué campo busca `q`: `todos` (por defecto), `paciente` o
                    `autor`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registros (puede estar vacío).",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = RegistroResponse.class)))),
            @ApiResponse(responseCode = "400", description = "`filtro` distinto de todos, paciente o autor.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/api/historial")
    public List<RegistroResponse> listarTodos(
            @Parameter(description = "Campo en el que busca `q`.", example = "paciente",
                    schema = @Schema(allowableValues = {"todos", "paciente", "autor"}))
            @RequestParam(required = false) String filtro,
            @Parameter(description = "Texto a buscar en el paciente o en el autor.", example = "ana")
            @RequestParam(required = false) String q) {
        return servicio.obtenerTodosLosRegistros(filtro, q);
    }

    @Operation(summary = "Listar el historial de un paciente",
            description = "Registros del paciente, del más reciente al más antiguo. `q` filtra por autor.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Registros del paciente (puede estar vacío).",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = RegistroResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese paciente activo.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/api/pacientes/{id}/historial")
    public List<RegistroResponse> listarPorPaciente(
            @Parameter(description = "ID del paciente.", example = "PAC-0001")
            @PathVariable String id,
            @Parameter(description = "Texto a buscar en el autor del registro.", example = "carlos")
            @RequestParam(required = false) String q) {
        return servicio.obtenerRegistrosPorPaciente(id, q);
    }

    /**
     * Crea un registro firmado por el usuario autenticado. El usuario se relee
     * del repositorio por su username (el nombre de la autenticación), nunca de
     * los claims del token.
     *
     * <p>Sin {@code Idempotency-Key}, como siempre. Con ella, repetir la petición
     * con la misma clave devuelve el mismo registro (con
     * {@code Idempotency-Replayed: true}) sin crear otro ni volver a descontar
     * stock; ver {@link HistorialIdempotenteService}.</p>
     */
    @Operation(summary = "Añadir un registro al historial",
            description = """
                    Crea un registro firmado por el trabajador del usuario autenticado; el `idAutor`
                    enviado, si viene, debe coincidir con él.

                    - `SIGNOS_VITALES` exige `signosVitales`; el resto de tipos, `contenido`.
                    - `MEDICACION` puede incluir `idMedicamento` y `cantidad` (los dos juntos) para
                      descontar ese stock en MedicamentosService al crear el registro.

                    Con la cabecera `Idempotency-Key` (16 a 100 caracteres: letras, dígitos, guion
                    o guion bajo), repetir la petición con la misma clave devuelve el mismo registro
                    con `Idempotency-Replayed: true`, sin crear otro ni descontar stock dos veces.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Registro creado (o repetido)."),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o autor que no coincide.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El usuario no tiene trabajador vinculado.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe el paciente activo o el medicamento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Stock insuficiente, medicamento vencido o " +
                    "clave de idempotencia reutilizada con otros datos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos o MedicamentosService " +
                    "no están disponibles.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/api/pacientes/{id}/historial")
    public ResponseEntity<RegistroResponse> agregarRegistro(
            @Parameter(description = "ID del paciente.", example = "PAC-0001")
            @PathVariable String id,
            @Valid @RequestBody RegistroRequest request,
            @Parameter(in = ParameterIn.HEADER,
                    description = "Clave que identifica el intento. Si se repite con los mismos "
                            + "datos, no se crea otro registro.",
                    example = "3f2c9a1e-8b7d-4c6f-9a2e-1d5b8c7f0a34")
            @RequestHeader(name = CABECERA_IDEMPOTENCIA, required = false) String clave,
            Authentication autenticacion) {
        Usuario usuario = usuarioService.buscarPorUsername(autenticacion.getName());
        if (clave == null) {
            RegistroResponse creado = servicio.agregarRegistroComo(
                    usuario, id, request.idAutor(), request.tipo(), request.contenido(),
                    request.signosVitales(), request.idMedicamento(), request.cantidad());
            return ResponseEntity.status(201).body(creado);
        }
        HistorialIdempotenteService.Resultado resultado =
                historialIdempotente.agregarRegistro(usuario, id, request, clave);
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.status(201);
        if (resultado.repetido()) {
            respuesta.header(CABECERA_REPETIDA, "true");
        }
        return respuesta.body(resultado.registro());
    }
}
