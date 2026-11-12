package com.pachoclosystem.pachoclosystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Alta o edición de un paciente.")
public record PacienteRequest(
        @Schema(description = "Nombre completo: letras y espacios (3 a 60 caracteres).",
                example = "Ana López")
        @NotBlank(message = "El nombre completo es obligatorio.")
        @Pattern(regexp = "^[A-Za-zÁÉÍÓÚÑÜáéíóúñü][A-Za-zÁÉÍÓÚÑÜáéíóúñü\\s]{2,59}$",
                message = "El nombre debe tener letras y espacios (3 a 60 caracteres).")
        String nombre,

        @Schema(description = "Edad en años.", example = "34", minimum = "0", maximum = "120")
        @NotNull(message = "La edad es obligatoria.")
        @Min(value = 0, message = "La edad debe ser un número entero entre 0 y 120.")
        @Max(value = 120, message = "La edad debe ser un número entero entre 0 y 120.")
        Integer edad,

        @Schema(description = "Número de habitación, entre 1 y 999.", example = "204",
                minimum = "1", maximum = "999")
        @NotNull(message = "El número de habitación es obligatorio.")
        @Min(value = 1, message = "El número de habitación debe ser un entero entre 1 y 999.")
        @Max(value = 999, message = "El número de habitación debe ser un entero entre 1 y 999.")
        Integer habitacion) {
}
