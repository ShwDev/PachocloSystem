package com.pachoclosystem.pachoclosystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Cambio de la contraseña propia ({@code POST /api/auth/password}): exige la
 * actual y la nueva, que debe cumplir la política. Los tokens emitidos antes
 * quedan revocados.
 */
@Schema(description = "Cambio de la contraseña del usuario autenticado.")
public record CambiarPasswordRequest(
        @Schema(description = "Contraseña actual.", example = "temporal-ana-2026",
                accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "La contraseña actual es obligatoria.") String passwordActual,

        @Schema(description = "Contraseña nueva: al menos 10 caracteres, hasta 72 bytes en UTF-8 y "
                + "distinta del username.", example = "nueva-clave-ana-2026",
                accessMode = Schema.AccessMode.WRITE_ONLY)
        @NotBlank(message = "La nueva contraseña es obligatoria.") String passwordNueva) {

    /** Las contraseñas nunca aparecen en {@code toString()} (logs, errores). */
    @Override
    public String toString() {
        return "CambiarPasswordRequest{passwordActual=***, passwordNueva=***}";
    }
}
