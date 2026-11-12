package com.pachoclosystem.pachoclosystem.dto;

import com.pachoclosystem.pachoclosystem.model.NivelExperiencia;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

@Schema(description = "Alta o edición de un doctor o enfermero.")
public record TrabajadorRequest(
        @Schema(description = "Nombre completo: solo letras y espacios (3 a 60 caracteres).",
                example = "Carlos Ruiz")
        @NotBlank(message = "El nombre completo es obligatorio.")
        @Pattern(regexp = "^[A-Za-zÁÉÍÓÚÑÜáéíóúñü][A-Za-zÁÉÍÓÚÑÜáéíóúñü\\s]{2,59}$",
                message = "El nombre debe tener solo letras y espacios (3 a 60 caracteres).")
        String nombre,

        @Schema(description = "Rol del trabajador.", example = "Doctor",
                allowableValues = {"Doctor", "Enfermero"})
        @NotBlank(message = "Debe seleccionar un rol.")
        @Pattern(regexp = "Doctor|Enfermero", message = "El rol debe ser Doctor o Enfermero.")
        String rol,

        @Schema(description = "Especialidad. Solo para doctores.", example = "Cardiología")
        String especialidad,

        @Schema(description = "Nivel de experiencia. Solo para enfermeros.", example = "AVANZADO",
                allowableValues = {"NOVATO", "PRINCIPIANTE", "AVANZADO"})
        NivelExperiencia nivelExperiencia) {
}
