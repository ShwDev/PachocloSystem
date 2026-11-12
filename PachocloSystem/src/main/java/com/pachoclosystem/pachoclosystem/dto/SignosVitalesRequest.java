package com.pachoclosystem.pachoclosystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Signos vitales de un registro de tipo SIGNOS_VITALES.")
public record SignosVitalesRequest(
        @Schema(description = "Temperatura en °C.", example = "36.8", minimum = "30.0",
                maximum = "45.0")
        @NotNull(message = "Temperatura: es obligatoria.")
        @DecimalMin(value = "30.0", message = "Temperatura: debe ser un número entre 30.0 y 45.0 °C.")
        @DecimalMax(value = "45.0", message = "Temperatura: debe ser un número entre 30.0 y 45.0 °C.")
        Double temperatura,

        @Schema(description = "Frecuencia cardíaca en lpm.", example = "78", minimum = "20",
                maximum = "250")
        @NotNull(message = "Frecuencia Cardíaca: es obligatoria.")
        @Min(value = 20, message = "Frecuencia Cardíaca: debe ser entre 20 y 250 lpm.")
        @Max(value = 250, message = "Frecuencia Cardíaca: debe ser entre 20 y 250 lpm.")
        Integer frecCardiaca,

        @Schema(description = "Presión sistólica en mmHg.", example = "120", minimum = "50",
                maximum = "250")
        @NotNull(message = "Presión Sistólica: es obligatoria.")
        @Min(value = 50, message = "Presión Sistólica: debe ser entre 50 y 250 mmHg.")
        @Max(value = 250, message = "Presión Sistólica: debe ser entre 50 y 250 mmHg.")
        Integer presionSistolica,

        @Schema(description = "Presión diastólica en mmHg.", example = "80", minimum = "30",
                maximum = "150")
        @NotNull(message = "Presión Diastólica: es obligatoria.")
        @Min(value = 30, message = "Presión Diastólica: debe ser entre 30 y 150 mmHg.")
        @Max(value = 150, message = "Presión Diastólica: debe ser entre 30 y 150 mmHg.")
        Integer presionDiastolica,

        @Schema(description = "Frecuencia respiratoria en rpm.", example = "16", minimum = "5",
                maximum = "60")
        @NotNull(message = "Frecuencia Respiratoria: es obligatoria.")
        @Min(value = 5, message = "Frecuencia Respiratoria: debe ser entre 5 y 60 rpm.")
        @Max(value = 60, message = "Frecuencia Respiratoria: debe ser entre 5 y 60 rpm.")
        Integer frecRespiratoria,

        @Schema(description = "Saturación de oxígeno en porcentaje.", example = "98", minimum = "0",
                maximum = "100")
        @NotNull(message = "Saturación de Oxígeno: es obligatoria.")
        @Min(value = 0, message = "Saturación de Oxígeno: debe ser entre 0 y 100%.")
        @Max(value = 100, message = "Saturación de Oxígeno: debe ser entre 0 y 100%.")
        Integer saturacion,

        @Schema(description = "Observaciones libres.", example = "Paciente estable.")
        String observaciones) {
}
