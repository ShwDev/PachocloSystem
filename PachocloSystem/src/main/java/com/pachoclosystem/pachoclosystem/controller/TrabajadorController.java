package com.pachoclosystem.pachoclosystem.controller;

import com.pachoclosystem.pachoclosystem.dto.ErrorResponse;
import com.pachoclosystem.pachoclosystem.dto.TrabajadorRequest;
import com.pachoclosystem.pachoclosystem.dto.TrabajadorResponse;
import com.pachoclosystem.pachoclosystem.model.TrabajadorHospital;
import com.pachoclosystem.pachoclosystem.service.TrabajadorService;
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
@RequestMapping("/api/trabajadores")
@Tag(name = "Trabajadores", description = """
        Doctores y enfermeros del hospital. Solo ADMIN puede crearlos, editarlos y darlos de baja;
        ADMIN y DOCTOR pueden consultarlos.
        """)
public class TrabajadorController {

    private final TrabajadorService servicio;

    public TrabajadorController(TrabajadorService servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Listar trabajadores",
            description = "Devuelve doctores y enfermeros. `q` filtra por ID, nombre, rol o "
                    + "especialidad.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trabajadores (puede estar vacío).",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = TrabajadorResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede consultar trabajadores.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public List<TrabajadorResponse> listar(
            @Parameter(description = "Texto a buscar en el ID, el nombre, el rol o la especialidad.",
                    example = "cardiología")
            @RequestParam(required = false) String q) {
        return servicio.listarTrabajadores(q).stream().map(TrabajadorResponse::from).toList();
    }

    @Operation(summary = "Obtener un trabajador", description = "Busca por su ID (`DOC-0001`, `ENF-0001`).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trabajador encontrado."),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede consultar trabajadores.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese trabajador.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public TrabajadorResponse obtener(
            @Parameter(description = "ID del trabajador.", example = "DOC-0001")
            @PathVariable String id) {
        return TrabajadorResponse.from(servicio.obtenerTrabajador(id));
    }

    @Operation(summary = "Registrar un trabajador",
            description = """
                    Solo ADMIN. El prefijo del ID depende del rol: `DOC-####` para doctores y
                    `ENF-####` para enfermeros. Un doctor lleva `especialidad`; un enfermero, su
                    `nivelExperiencia` (`NOVATO`, `PRINCIPIANTE` o `AVANZADO`).
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Trabajador registrado."),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede registrar trabajadores.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<TrabajadorResponse> registrar(@Valid @RequestBody TrabajadorRequest request) {
        TrabajadorHospital t = servicio.registrarTrabajador(
                request.nombre(), request.rol(), request.especialidad(), request.nivelExperiencia());
        return ResponseEntity.created(URI.create("/api/trabajadores/" + t.getIdTrabajador()))
                .body(TrabajadorResponse.from(t));
    }

    @Operation(summary = "Editar un trabajador",
            description = "Solo ADMIN. Cambiar el rol exige enviar también los datos del nuevo rol.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Trabajador actualizado."),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede editar trabajadores.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese trabajador.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "El cambio deja datos incompatibles "
                    + "(por ejemplo, un usuario vinculado con otro rol).",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public TrabajadorResponse editar(
            @Parameter(description = "ID del trabajador.", example = "DOC-0001")
            @PathVariable String id,
            @Valid @RequestBody TrabajadorRequest request) {
        return TrabajadorResponse.from(servicio.editarTrabajador(
                id, request.nombre(), request.rol(), request.especialidad(), request.nivelExperiencia()));
    }

    @Operation(summary = "Eliminar un trabajador (borrado físico)",
            description = """
                    Solo ADMIN. **Borrado físico:** elimina el documento del trabajador de MongoDB
                    (colección `trabajadores`) y, en la misma transacción, **desactiva su
                    usuario vinculado** e invalida sus tokens de inmediato. El historial que firmó
                    se conserva.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Trabajador eliminado, sin cuerpo."),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede dar de baja trabajadores.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese trabajador.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @Parameter(description = "ID del trabajador.", example = "DOC-0001")
            @PathVariable String id) {
        servicio.eliminarTrabajador(id);
        return ResponseEntity.noContent().build();
    }
}
