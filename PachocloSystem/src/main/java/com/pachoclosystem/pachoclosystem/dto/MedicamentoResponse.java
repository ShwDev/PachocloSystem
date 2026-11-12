package com.pachoclosystem.pachoclosystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDate;

/** Medicamento tal como lo devuelve MedicamentosService. */
@Schema(description = "Medicamento del inventario, con los indicadores calculados por el servicio.")
public record MedicamentoResponse(
        @Schema(description = "ID del medicamento.", example = "MED-0001")
        String idMedicamento,

        @Schema(description = "Nombre comercial.", example = "Ibuprofeno")
        String nombre,

        @Schema(description = "Principio activo.", example = "Ibuprofeno")
        String principioActivo,

        @Schema(description = "Forma farmacéutica.", example = "TABLETA")
        String presentacion,

        @Schema(description = "Concentración con su unidad.", example = "400 mg")
        String concentracion,

        @Schema(description = "Laboratorio fabricante.", example = "Genfar")
        String laboratorio,

        @Schema(description = "Lote del fabricante.", example = "L-2026-01")
        String lote,

        @Schema(description = "Unidades disponibles.", example = "100")
        int cantidadStock,

        @Schema(description = "Umbral de stock bajo.", example = "20")
        int stockMinimo,

        @Schema(description = "Fecha de vencimiento.", example = "2027-01-31",
                type = "string", format = "date")
        LocalDate fechaVencimiento,

        @Schema(description = "Dónde se guarda.", example = "Farmacia, estante A")
        String ubicacion,

        @Schema(description = "`true` si el stock es igual o inferior al mínimo.", example = "false")
        boolean stockBajo,

        @Schema(description = "`true` si la fecha de vencimiento es anterior a hoy.", example = "false")
        boolean vencido) {
}
