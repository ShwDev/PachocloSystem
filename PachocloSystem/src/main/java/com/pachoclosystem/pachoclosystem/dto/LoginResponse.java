package com.pachoclosystem.pachoclosystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Respuesta del login: el token JWT bearer, el tipo de token, el tiempo de
 * validez en segundos, el rol del usuario autenticado y si tiene la contraseña
 * pendiente de cambio (en ese caso solo puede cambiarla).
 */
@Schema(description = "Token emitido por el login y estado del usuario.")
public record LoginResponse(
        @Schema(description = "Token JWT para `Authorization: Bearer <token>`.")
        String token,

        @Schema(description = "Tipo de token.", example = "Bearer")
        String tipo,

        @Schema(description = "Segundos que faltan para que el token caduque.", example = "1800")
        long expiraEnSegundos,

        @Schema(description = "Rol del usuario.", example = "ADMIN",
                allowableValues = {"ADMIN", "DOCTOR", "ENFERMERO"})
        String rol,

        @Schema(description = "`true` si debe cambiar la contraseña antes de operar.", example = "false")
        boolean debeCambiarPassword) {
}
