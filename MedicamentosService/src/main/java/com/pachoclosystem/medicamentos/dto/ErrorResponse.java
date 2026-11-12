package com.pachoclosystem.medicamentos.dto;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.List;

/** Cuerpo uniforme de todos los errores de la API. */
@Schema(description = "Error de la API: mismo formato para todos los estados HTTP.")
public record ErrorResponse(
        @Schema(description = "Código de estado HTTP.", example = "409")
        int status,

        @Schema(description = "Motivo del estado HTTP.", example = "Conflict")
        String error,

        @Schema(description = "Mensajes para mostrar al usuario, en español y sin detalles internos.",
                example = "[\"Stock insuficiente: disponible 20, solicitado 25.\"]")
        List<String> mensajes) {
}
