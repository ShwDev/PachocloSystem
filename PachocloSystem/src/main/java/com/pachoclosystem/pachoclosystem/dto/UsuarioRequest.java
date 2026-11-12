package com.pachoclosystem.pachoclosystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Alta de un usuario ({@code POST /api/usuarios}). Las reglas (formato del
 * username, política de contraseña, rol y vínculo con el trabajador) las valida
 * {@code UsuarioService}, que devuelve todos los errores juntos.
 *
 * @param rol {@code ADMIN}, {@code DOCTOR} o {@code ENFERMERO}
 * @param idTrabajador obligatorio para DOCTOR y ENFERMERO; vacío para ADMIN
 */
@Schema(description = "Alta de un usuario de acceso.")
public record UsuarioRequest(
        @Schema(description = "Nombre de usuario único: 3 a 30 caracteres (minúsculas, dígitos, "
                + "punto, guion bajo o guion).", example = "ana.lopez")
        String username,

        @Schema(description = "Contraseña: al menos 10 caracteres, hasta 72 bytes en UTF-8 y "
                + "distinta del username.", accessMode = Schema.AccessMode.WRITE_ONLY)
        String password,

        @Schema(description = "Rol del usuario.", example = "DOCTOR",
                allowableValues = {"ADMIN", "DOCTOR", "ENFERMERO"})
        String rol,

        @Schema(description = "ID del trabajador vinculado. Obligatorio para DOCTOR y ENFERMERO; "
                + "vacío para ADMIN.", example = "DOC-0001")
        String idTrabajador) {

    /** La contraseña nunca aparece en {@code toString()} (logs, errores). */
    @Override
    public String toString() {
        return "UsuarioRequest{username='" + username + "', rol=" + rol
                + ", idTrabajador=" + idTrabajador + "}";
    }
}
