package com.pachoclosystem.medicamentos.controller;

import com.pachoclosystem.medicamentos.dto.ActualizarMedicamentoRequest;
import com.pachoclosystem.medicamentos.dto.CrearMedicamentoRequest;
import com.pachoclosystem.medicamentos.dto.ErrorResponse;
import com.pachoclosystem.medicamentos.dto.MedicamentoResponse;
import com.pachoclosystem.medicamentos.dto.MovimientoStockRequest;
import com.pachoclosystem.medicamentos.model.Medicamento;
import com.pachoclosystem.medicamentos.service.MedicamentoService;
import com.pachoclosystem.medicamentos.service.SalidasIdempotentesService;
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
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/medicamentos")
@Tag(name = "Medicamentos", description = """
        Alta, consulta, edición y baja del inventario, entradas y salidas de stock, y listados
        de stock bajo, próximos a vencer y vencidos.
        """)
public class MedicamentoController {

    /** Clave opcional para que repetir una salida no vuelva a descontar. */
    public static final String CABECERA_IDEMPOTENCIA = "Idempotency-Key";
    /** En la respuesta: {@code true} si es la repetición de una salida ya hecha con esa clave. */
    public static final String CABECERA_REPETIDA = "Idempotency-Replayed";

    private final MedicamentoService servicio;
    private final SalidasIdempotentesService salidasIdempotentes;

    public MedicamentoController(MedicamentoService servicio,
                                 SalidasIdempotentesService salidasIdempotentes) {
        this.servicio = servicio;
        this.salidasIdempotentes = salidasIdempotentes;
    }

    @Operation(summary = "Listar medicamentos",
            description = "Devuelve todo el inventario. `q` filtra, sin distinguir mayúsculas, "
                    + "por ID, nombre comercial o principio activo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Inventario (puede estar vacío).",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = MedicamentoResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Falta `X-Api-Key` o no es válida.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public List<MedicamentoResponse> listar(
            @Parameter(description = "Texto a buscar en el ID, el nombre o el principio activo.",
                    example = "ibuprofeno")
            @RequestParam(required = false) String q) {
        return respuestas(servicio.listarMedicamentos(q));
    }

    @Operation(summary = "Obtener un medicamento", description = "Busca por su ID (`MED-0001`).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamento encontrado."),
            @ApiResponse(responseCode = "401", description = "Falta `X-Api-Key` o no es válida.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese medicamento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public MedicamentoResponse obtener(
            @Parameter(description = "ID del medicamento.", example = "MED-0001")
            @PathVariable String id) {
        return respuesta(servicio.obtenerMedicamento(id));
    }

    @Operation(summary = "Registrar un medicamento",
            description = "Da de alta un medicamento con su stock inicial. El ID lo asigna el "
                    + "servicio (`MED-0001`, `MED-0002`, ...) y la respuesta incluye la cabecera "
                    + "`Location` con la URL del recurso creado.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Medicamento registrado."),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta `X-Api-Key` o no es válida.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409",
                    description = "Ya existe un medicamento con el mismo nombre, concentración, "
                            + "presentación y lote.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<MedicamentoResponse> registrar(
            @Valid @RequestBody CrearMedicamentoRequest request) {
        Medicamento medicamento = servicio.registrarMedicamento(request.datos(), request.cantidadStock());
        return ResponseEntity.created(URI.create("/api/medicamentos/" + medicamento.getIdMedicamento()))
                .body(respuesta(medicamento));
    }

    @Operation(summary = "Editar un medicamento",
            description = "Actualiza los datos descriptivos. El stock **no** se edita aquí: solo "
                    + "cambia con entradas y salidas. La fecha de vencimiento puede ser pasada para "
                    + "corregir un registro.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamento actualizado."),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o incompletos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta `X-Api-Key` o no es válida.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese medicamento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Los datos nuevos duplican otro medicamento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{id}")
    public MedicamentoResponse editar(
            @Parameter(description = "ID del medicamento.", example = "MED-0001")
            @PathVariable String id,
            @Valid @RequestBody ActualizarMedicamentoRequest request) {
        return respuesta(servicio.editarMedicamento(id, request.datos()));
    }

    @Operation(summary = "Eliminar un medicamento",
            description = "**Borrado físico:** elimina el documento de MongoDB (colección `medicamentos`). "
                    + "La operación no se puede deshacer; después, `GET /{id}` responde 404.")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Medicamento eliminado, sin cuerpo."),
            @ApiResponse(responseCode = "401", description = "Falta `X-Api-Key` o no es válida.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese medicamento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(
            @Parameter(description = "ID del medicamento.", example = "MED-0001")
            @PathVariable String id) {
        servicio.eliminarMedicamento(id);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Registrar una entrada de stock",
            description = "Suma unidades al stock de forma atómica. Falla si el resultado supera "
                    + "el máximo admitido (1 000 000).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock actualizado."),
            @ApiResponse(responseCode = "400", description = "Cantidad inválida o stock máximo superado.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta `X-Api-Key` o no es válida.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese medicamento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/entradas")
    public MedicamentoResponse registrarEntrada(
            @Parameter(description = "ID del medicamento.", example = "MED-0001")
            @PathVariable String id,
            @Valid @RequestBody MovimientoStockRequest request) {
        return respuesta(servicio.registrarEntrada(id, request.cantidad()));
    }

    /**
     * Sin {@code Idempotency-Key}, una salida como siempre. Con ella, repetir la
     * petición con la misma clave y los mismos datos devuelve la misma respuesta
     * (con {@code Idempotency-Replayed: true}) sin volver a descontar.
     */
    @Operation(summary = "Registrar una salida de stock",
            description = """
                    Descuenta unidades del stock de forma atómica.

                    Con la cabecera `Idempotency-Key` (16 a 100 caracteres: letras, dígitos, guion
                    o guion bajo), repetir la petición con la misma clave y los mismos datos
                    devuelve la misma respuesta con `Idempotency-Replayed: true`, sin volver a
                    descontar. Usar la misma clave para otro medicamento o cantidad responde 409.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Stock actualizado (o repetido)."),
            @ApiResponse(responseCode = "400", description = "Cantidad inválida.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta `X-Api-Key` o no es válida.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese medicamento.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = """
                    Stock insuficiente, medicamento vencido o clave de idempotencia reutilizada
                    con otros datos.
                    """,
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/{id}/salidas")
    public ResponseEntity<MedicamentoResponse> registrarSalida(
            @Parameter(description = "ID del medicamento.", example = "MED-0001")
            @PathVariable String id,
            @Valid @RequestBody MovimientoStockRequest request,
            @Parameter(description = "Clave que identifica el intento de salida. Si se repite con "
                    + "los mismos datos, la operación no se vuelve a aplicar.",
                    example = "3f2c9a1e-8b7d-4c6f-9a2e-1d5b8c7f0a34")
            @RequestHeader(name = CABECERA_IDEMPOTENCIA, required = false) String clave) {
        if (clave == null) {
            return ResponseEntity.ok(respuesta(servicio.registrarSalida(id, request.cantidad())));
        }
        SalidasIdempotentesService.Resultado resultado =
                salidasIdempotentes.registrarSalida(id, request.cantidad(), clave);
        ResponseEntity.BodyBuilder respuesta = ResponseEntity.ok();
        if (resultado.repetida()) {
            respuesta.header(CABECERA_REPETIDA, "true");
        }
        return respuesta.body(resultado.respuesta());
    }

    @Operation(summary = "Listar medicamentos con stock bajo",
            description = "Los que tienen un stock igual o inferior a su stock mínimo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamentos con stock bajo."),
            @ApiResponse(responseCode = "401", description = "Falta `X-Api-Key` o no es válida.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/stock-bajo")
    public List<MedicamentoResponse> listarStockBajo() {
        return respuestas(servicio.listarStockBajo());
    }

    @Operation(summary = "Listar medicamentos próximos a vencer",
            description = "No vencidos que vencen entre hoy y dentro de `dias` días, del más "
                    + "próximo al más lejano.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamentos próximos a vencer."),
            @ApiResponse(responseCode = "400", description = "`dias` fuera del rango 1..365.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta `X-Api-Key` o no es válida.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/por-vencer")
    public List<MedicamentoResponse> listarPorVencer(
            @Parameter(description = "Días desde hoy, entre 1 y 365.", example = "30")
            @RequestParam(defaultValue = "30") int dias) {
        return respuestas(servicio.listarPorVencer(dias));
    }

    @Operation(summary = "Listar medicamentos vencidos",
            description = "Los que tienen la fecha de vencimiento anterior a hoy.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Medicamentos vencidos."),
            @ApiResponse(responseCode = "401", description = "Falta `X-Api-Key` o no es válida.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/vencidos")
    public List<MedicamentoResponse> listarVencidos() {
        return respuestas(servicio.listarVencidos());
    }

    private MedicamentoResponse respuesta(Medicamento medicamento) {
        return MedicamentoResponse.from(medicamento, servicio.hoy());
    }

    private List<MedicamentoResponse> respuestas(List<Medicamento> medicamentos) {
        LocalDate hoy = servicio.hoy();
        return medicamentos.stream().map(m -> MedicamentoResponse.from(m, hoy)).toList();
    }
}
