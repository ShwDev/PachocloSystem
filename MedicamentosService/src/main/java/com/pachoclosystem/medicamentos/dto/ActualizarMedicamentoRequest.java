package com.pachoclosystem.medicamentos.dto;

import com.pachoclosystem.medicamentos.model.DatosMedicamento;
import com.pachoclosystem.medicamentos.model.Presentacion;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * Edición de los datos de un medicamento. El stock no se edita aquí: solo cambia
 * con entradas y salidas. La fecha puede ser pasada para corregir un registro.
 */
@Schema(description = "Datos para editar un medicamento. El stock no se modifica aquí.")
public record ActualizarMedicamentoRequest(
        @Schema(description = "Nombre comercial.", example = "Ibuprofeno", maxLength = 80)
        @NotBlank(message = "El nombre es obligatorio.")
        @Size(max = 80, message = "El nombre no puede superar 80 caracteres.")
        String nombre,

        @Schema(description = "Principio activo.", example = "Ibuprofeno", maxLength = 80)
        @NotBlank(message = "El principio activo es obligatorio.")
        @Size(max = 80, message = "El principio activo no puede superar 80 caracteres.")
        String principioActivo,

        @Schema(description = "Forma farmacéutica.", example = "TABLETA",
                allowableValues = {"TABLETA", "CAPSULA", "JARABE", "SUSPENSION", "INYECTABLE",
                        "CREMA", "GOTAS", "OTRO"})
        @NotNull(message = "La presentación es obligatoria.")
        Presentacion presentacion,

        @Schema(description = "Concentración con su unidad.", example = "400 mg", maxLength = 40)
        @NotBlank(message = "La concentración es obligatoria.")
        @Size(max = 40, message = "La concentración no puede superar 40 caracteres.")
        String concentracion,

        @Schema(description = "Laboratorio fabricante.", example = "Genfar", maxLength = 80)
        @NotBlank(message = "El laboratorio es obligatorio.")
        @Size(max = 80, message = "El laboratorio no puede superar 80 caracteres.")
        String laboratorio,

        @Schema(description = "Lote del fabricante.", example = "L-2026-01", maxLength = 40)
        @NotBlank(message = "El lote es obligatorio.")
        @Size(max = 40, message = "El lote no puede superar 40 caracteres.")
        String lote,

        @Schema(description = "Umbral a partir del cual se considera stock bajo.", example = "20",
                minimum = "0", maximum = "1000000")
        @NotNull(message = "El stock mínimo es obligatorio.")
        @Min(value = 0, message = "El stock mínimo debe estar entre 0 y 1000000.")
        @Max(value = 1_000_000, message = "El stock mínimo debe estar entre 0 y 1000000.")
        Integer stockMinimo,

        @Schema(description = "Fecha de vencimiento (ISO 8601). Puede ser pasada.", example = "2027-01-31",
                type = "string", format = "date")
        @NotNull(message = "La fecha de vencimiento es obligatoria.")
        LocalDate fechaVencimiento,

        @Schema(description = "Dónde se guarda.", example = "Farmacia, estante A", maxLength = 80)
        @NotBlank(message = "La ubicación de almacenamiento es obligatoria.")
        @Size(max = 80, message = "La ubicación no puede superar 80 caracteres.")
        String ubicacion) {

    public DatosMedicamento datos() {
        return new DatosMedicamento(nombre, principioActivo, presentacion, concentracion,
                laboratorio, lote, stockMinimo, fechaVencimiento, ubicacion);
    }
}
