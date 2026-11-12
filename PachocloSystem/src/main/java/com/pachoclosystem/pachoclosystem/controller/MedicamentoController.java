package com.pachoclosystem.pachoclosystem.controller;

import com.pachoclosystem.pachoclosystem.dto.ActualizarMedicamentoRequest;
import com.pachoclosystem.pachoclosystem.dto.ErrorResponse;
import com.pachoclosystem.pachoclosystem.dto.MedicamentoRequest;
import com.pachoclosystem.pachoclosystem.dto.MedicamentoResponse;
import com.pachoclosystem.pachoclosystem.dto.MovimientoStockRequest;
import com.pachoclosystem.pachoclosystem.service.InventarioMedicamentosService;
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

/**
 * Inventario de medicamentos a través del servicio principal (requiere token,
 * como el resto de {@code /api/**}). Cada operación se reenvía a MedicamentosService.
 */
@RestController
@RequestMapping("/api/medicamentos")
@Tag(name = "Medicamentos", description = """
        Inventario a través de MedicamentosService. Requiere token como el resto de la API; el
        servicio interno añade su propia clave. Los errores del microservicio se traducen al
        mismo cuerpo `ErrorResponse`.
        """)
public class MedicamentoController {

    private final InventarioMedicamentosService servicio;

    public MedicamentoController(InventarioMedicamentosService servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Listar medicamentos",
            description = "Devuelve todo el inventario. `q` filtra por ID, nombre o principio activo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventario (puede estar vacío).",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = MedicamentoResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "MedicamentosService devolvió una " +
                    "respuesta inesperada.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos o MedicamentosService " +
                    "no están disponibles.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public List<MedicamentoResponse> listar(
            @Parameter(description = "Texto a buscar en el ID, el nombre o el principio activo.",
                    example = "ibuprofeno")
            @RequestParam(required = false) String q) {
        return servicio.listar(q);
    }

    @Operation(summary = "Obtener un medicamento", description = "Busca por su ID (`MED-0001`).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamento encontrado."),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese medicamento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "MedicamentosService devolvió una " +
                    "respuesta inesperada.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos o MedicamentosService " +
                    "no están disponibles.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public MedicamentoResponse obtener(
            @Parameter(description = "ID del medicamento.", example = "MED-0001")
            @PathVariable String id) {
        return servicio.obtener(id);
    }

    @Operation(summary = "Registrar un medicamento",
            description = "Solo ADMIN. Los datos se validan en MedicamentosService, que asigna el "
                    + "ID y devuelve la cabecera `Location`.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Medicamento registrado."),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede registrar medicamentos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe un medicamento con el mismo " +
                    "nombre, concentración, presentación y lote.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "MedicamentosService devolvió una " +
                    "respuesta inesperada.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos o MedicamentosService " +
                    "no están disponibles.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<MedicamentoResponse> registrar(@RequestBody MedicamentoRequest request) {
        MedicamentoResponse medicamento = servicio.registrar(request);
        return ResponseEntity.created(URI.create("/api/medicamentos/" + medicamento.idMedicamento()))
                .body(medicamento);
    }

    @Operation(summary = "Editar un medicamento",
            description = "Solo ADMIN. Actualiza los datos descriptivos; el stock solo cambia con "
                    + "entradas y salidas.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamento actualizado."),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede editar medicamentos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese medicamento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Los datos nuevos duplican otro medicamento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "MedicamentosService devolvió una " +
                    "respuesta inesperada.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos o MedicamentosService " +
                    "no están disponibles.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public MedicamentoResponse editar(
            @Parameter(description = "ID del medicamento.", example = "MED-0001")
            @PathVariable String id,
            @RequestBody ActualizarMedicamentoRequest request) {
        return servicio.editar(id, request);
    }

    @Operation(summary = "Eliminar un medicamento",
            description = "Solo ADMIN. Borra el medicamento del inventario; no se puede deshacer.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Medicamento eliminado, sin cuerpo."),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede eliminar medicamentos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese medicamento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "MedicamentosService devolvió una " +
                    "respuesta inesperada.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos o MedicamentosService " +
                    "no están disponibles.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @Parameter(description = "ID del medicamento.", example = "MED-0001")
            @PathVariable String id) {
        servicio.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Registrar una entrada de stock",
            description = "Solo ADMIN. Suma unidades de forma atómica.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock actualizado."),
            @ApiResponse(responseCode = "400", description = "Cantidad inválida o stock máximo superado.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede registrar entradas.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese medicamento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "MedicamentosService devolvió una " +
                    "respuesta inesperada.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos o MedicamentosService " +
                    "no están disponibles.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/entradas")
    public MedicamentoResponse registrarEntrada(
            @Parameter(description = "ID del medicamento.", example = "MED-0001")
            @PathVariable String id,
            @Valid @RequestBody MovimientoStockRequest request) {
        return servicio.registrarEntrada(id, request.cantidad());
    }

    @Operation(summary = "Registrar una salida de stock",
            description = """
                    Solo ADMIN y ENFERMERO. Descuenta unidades de forma atómica.

                    A través de esta ruta **no se reenvía** la cabecera `Idempotency-Key` al
                    microservicio: la operación se aplica una vez por petición.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock actualizado."),
            @ApiResponse(responseCode = "400", description = "Cantidad inválida.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no puede registrar salidas de stock.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese medicamento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Stock insuficiente o medicamento vencido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "MedicamentosService devolvió una " +
                    "respuesta inesperada.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos o MedicamentosService " +
                    "no están disponibles. Si no se pudo confirmar la salida, se puede reintentar.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/salidas")
    public MedicamentoResponse registrarSalida(
            @Parameter(description = "ID del medicamento.", example = "MED-0001")
            @PathVariable String id,
            @Valid @RequestBody MovimientoStockRequest request) {
        return servicio.registrarSalida(id, request.cantidad());
    }

    @Operation(summary = "Listar medicamentos con stock bajo",
            description = "Los que tienen un stock igual o inferior a su stock mínimo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamentos con stock bajo.",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = MedicamentoResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "MedicamentosService devolvió una " +
                    "respuesta inesperada.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos o MedicamentosService " +
                    "no están disponibles.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/stock-bajo")
    public List<MedicamentoResponse> listarStockBajo() {
        return servicio.listarStockBajo();
    }

    @Operation(summary = "Listar medicamentos próximos a vencer",
            description = "No vencidos que vencen entre hoy y dentro de `dias` días (por defecto 30).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamentos próximos a vencer.",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = MedicamentoResponse.class)))),
            @ApiResponse(responseCode = "400", description = "`dias` fuera del rango 1..365.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "MedicamentosService devolvió una " +
                    "respuesta inesperada.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos o MedicamentosService " +
                    "no están disponibles.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/por-vencer")
    public List<MedicamentoResponse> listarPorVencer(
            @Parameter(description = "Días desde hoy, entre 1 y 365.", example = "30")
            @RequestParam(required = false) Integer dias) {
        return servicio.listarPorVencer(dias);
    }

    @Operation(summary = "Listar medicamentos vencidos",
            description = "Los que tienen la fecha de vencimiento anterior a hoy.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamentos vencidos.",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = MedicamentoResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "502", description = "MedicamentosService devolvió una " +
                    "respuesta inesperada.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos o MedicamentosService " +
                    "no están disponibles.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/vencidos")
    public List<MedicamentoResponse> listarVencidos() {
        return servicio.listarVencidos();
    }
}
