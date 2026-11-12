package com.pachoclosystem.pachoclosystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/**
 * Credenciales del login. Username y contraseña son obligatorios.
 */
@Schema(description = "Credenciales de acceso.")
public record LoginRequest(
        @Schema(description = "Nombre de usuario.", example = "admin", minLength = 1)
        @NotBlank(message = "El username es obligatorio.") String username,

        @Schema(description = "Contraseña en claro.", example = "ContraseñaSegura1",
                accessMode = Schema.AccessMode.WRITE_ONLY, minLength = 1)
        @NotBlank(message = "La contraseña es obligatoria.") String password) {
}
