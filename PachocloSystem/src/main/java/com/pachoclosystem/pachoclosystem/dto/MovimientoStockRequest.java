package com.pachoclosystem.pachoclosystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** Entrada o salida de unidades de stock de un medicamento. */
@Schema(description = "Cantidad de unidades que se suman (entrada) o se restan (salida).")
public record MovimientoStockRequest(
        @Schema(description = "Unidades a mover. Entero entre 1 y 1 000 000.", example = "10",
                minimum = "1", maximum = "1000000")
        @NotNull(message = "La cantidad es obligatoria.")
        @Min(value = 1, message = "La cantidad debe ser un entero entre 1 y 1000000.")
        @Max(value = 1_000_000, message = "La cantidad debe ser un entero entre 1 y 1000000.")
        Integer cantidad) {
}
