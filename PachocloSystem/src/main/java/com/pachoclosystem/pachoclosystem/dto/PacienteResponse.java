package com.pachoclosystem.pachoclosystem.dto;

import com.pachoclosystem.pachoclosystem.model.Paciente;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Paciente activo.")
public record PacienteResponse(
        @Schema(description = "ID del paciente.", example = "PAC-0001")
        String idPaciente,

        @Schema(description = "Nombre completo.", example = "Ana López")
        String nombre,

        @Schema(description = "Edad en años.", example = "34")
        int edad,

        @Schema(description = "Número de habitación.", example = "204")
        int habitacion) {

    public static PacienteResponse from(Paciente p) {
        return new PacienteResponse(p.getIdPaciente(), p.getNombre(), p.getEdad(), p.getHabitacion());
    }
}
