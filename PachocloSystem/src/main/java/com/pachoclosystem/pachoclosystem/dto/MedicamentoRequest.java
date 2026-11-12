package com.pachoclosystem.pachoclosystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/**
 * Alta de un medicamento. Se reenvía tal cual a MedicamentosService, que es quien
 * valida los campos y devuelve los mensajes de error (se trasladan al cliente).
 */
@Schema(description = "Alta de un medicamento. Los datos se validan en MedicamentosService.")
public record MedicamentoRequest(
        @Schema(description = "Nombre comercial.", example = "Ibuprofeno", maxLength = 80)
        String nombre,

        @Schema(description = "Principio activo.", example = "Ibuprofeno", maxLength = 80)
        String principioActivo,

        @Schema(description = "Forma farmacéutica.", example = "TABLETA",
                allowableValues = {"TABLETA", "CAPSULA", "JARABE", "SUSPENSION", "INYECTABLE",
                        "CREMA", "GOTAS", "OTRO"})
        String presentacion,

        @Schema(description = "Concentración con su unidad.", example = "400 mg", maxLength = 40)
        String concentracion,

        @Schema(description = "Laboratorio fabricante.", example = "Genfar", maxLength = 80)
        String laboratorio,

        @Schema(description = "Lote del fabricante.", example = "L-2026-01", maxLength = 40)
        String lote,

        @Schema(description = "Unidades disponibles al registrarlo.", example = "100",
                minimum = "0", maximum = "1000000")
        Integer cantidadStock,

        @Schema(description = "Umbral a partir del cual se considera stock bajo.", example = "20",
                minimum = "0", maximum = "1000000")
        Integer stockMinimo,

        @Schema(description = "Fecha de vencimiento (ISO 8601).", example = "2027-01-31",
                type = "string", format = "date")
        LocalDate fechaVencimiento,

        @Schema(description = "Dónde se guarda.", example = "Farmacia, estante A", maxLength = 80)
        String ubicacion) {
}
