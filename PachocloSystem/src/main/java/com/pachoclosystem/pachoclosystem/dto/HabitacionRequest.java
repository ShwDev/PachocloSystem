package com.pachoclosystem.pachoclosystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Nueva habitación de un paciente.")
public record HabitacionRequest(
        @Schema(description = "Número de habitación, entre 1 y 999.", example = "204",
                minimum = "1", maximum = "999")
        @NotNull(message = "El número de habitación es obligatorio.")
        @Min(value = 1, message = "El número de habitación debe ser un entero entre 1 y 999.")
        @Max(value = 999, message = "El número de habitación debe ser un entero entre 1 y 999.")
        Integer habitacion) {
}
