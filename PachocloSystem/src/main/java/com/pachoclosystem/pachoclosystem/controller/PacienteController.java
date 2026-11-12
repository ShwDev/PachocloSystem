package com.pachoclosystem.pachoclosystem.controller;

import com.pachoclosystem.pachoclosystem.dto.ErrorResponse;
import com.pachoclosystem.pachoclosystem.dto.HabitacionRequest;
import com.pachoclosystem.pachoclosystem.dto.PacienteRequest;
import com.pachoclosystem.pachoclosystem.dto.PacienteResponse;
import com.pachoclosystem.pachoclosystem.model.Paciente;
import com.pachoclosystem.pachoclosystem.service.PacienteService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;

@RestController
@RequestMapping("/api/pacientes")
@Tag(name = "Pacientes", description = """
        Alta, consulta, edición, cambio de habitación y baja lógica. La baja conserva el
        historial del paciente.
        """)
public class PacienteController {

    private final PacienteService servicio;

    public PacienteController(PacienteService servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Listar pacientes",
            description = "Devuelve los pacientes activos. `q` filtra por ID, nombre o habitación.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Pacientes activos (puede estar vacío).",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = PacienteResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public List<PacienteResponse> listar(
            @Parameter(description = "Texto a buscar en el ID, el nombre o la habitación.",
                    example = "ana")
            @RequestParam(required = false) String q) {
        return servicio.listarPacientes(q).stream().map(PacienteResponse::from).toList();
    }

    @Operation(summary = "Obtener un paciente", description = "Busca por su ID (`PAC-0001`).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paciente encontrado."),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese paciente activo.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public PacienteResponse obtener(
            @Parameter(description = "ID del paciente.", example = "PAC-0001")
            @PathVariable String id) {
        return PacienteResponse.from(servicio.obtenerPaciente(id));
    }

    @Operation(summary = "Registrar un paciente",
            description = "Solo ADMIN y DOCTOR. El ID lo asigna el sistema (`PAC-0001`, ...) y la "
                    + "respuesta incluye la cabecera `Location`.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Paciente registrado."),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede registrar pacientes.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<PacienteResponse> registrar(@Valid @RequestBody PacienteRequest request) {
        Paciente paciente = servicio.registrarPaciente(request.nombre(), request.edad(), request.habitacion());
        return ResponseEntity.created(URI.create("/api/pacientes/" + paciente.getIdPaciente()))
                .body(PacienteResponse.from(paciente));
    }

    @Operation(summary = "Editar un paciente",
            description = "Solo ADMIN y DOCTOR. Cambia nombre, edad y habitación.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Paciente actualizado."),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede editar pacientes.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese paciente activo.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public PacienteResponse editar(
            @Parameter(description = "ID del paciente.", example = "PAC-0001")
            @PathVariable String id,
            @Valid @RequestBody PacienteRequest request) {
        return PacienteResponse.from(
                servicio.editarPaciente(id, request.nombre(), request.edad(), request.habitacion()));
    }

    @Operation(summary = "Cambiar la habitación",
            description = "Todos los roles autenticados pueden cambiar la habitación de un paciente.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Habitación actualizada."),
            @ApiResponse(responseCode = "400", description = "Habitación fuera del rango 1..999.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese paciente activo.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/habitacion")
    public PacienteResponse editarHabitacion(
            @Parameter(description = "ID del paciente.", example = "PAC-0001")
            @PathVariable String id,
            @Valid @RequestBody HabitacionRequest request) {
        return PacienteResponse.from(servicio.editarHabitacion(id, request.habitacion()));
    }

    @Operation(summary = "Dar de baja un paciente",
            description = """
                    Solo ADMIN. Es una **baja lógica**: el paciente deja de aparecer en los listados,
                    pero su historial clínico se conserva y sigue siendo consultable.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Paciente dado de baja, sin cuerpo."),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede dar de baja pacientes.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese paciente activo.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @Parameter(description = "ID del paciente.", example = "PAC-0001")
            @PathVariable String id) {
        servicio.eliminarPaciente(id);
        return ResponseEntity.noContent().build();
    }
}
