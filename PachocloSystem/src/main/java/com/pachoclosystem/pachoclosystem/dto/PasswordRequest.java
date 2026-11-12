package com.pachoclosystem.pachoclosystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/** Nueva contraseña al restablecerla ({@code PATCH /api/usuarios/{id}/password}). */
@Schema(description = "Contraseña nueva asignada por un administrador.")
public record PasswordRequest(
        @Schema(description = "Contraseña nueva: al menos 10 caracteres, hasta 72 bytes en UTF-8 y "
                + "distinta del username.", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password) {

    /** La contraseña nunca aparece en {@code toString()} (logs, errores). */
    @Override
    public String toString() {
        return "PasswordRequest{password=***}";
    }
}
