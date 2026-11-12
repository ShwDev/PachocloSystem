package com.pachoclosystem.pachoclosystem.dto;

import com.pachoclosystem.pachoclosystem.model.Usuario;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Usuario de acceso ({@code GET /api/auth/me} y {@code /api/usuarios}). Nunca
 * incluye el hash de la contraseña ni la versión del token (un detalle interno
 * del JWT). {@code debeCambiarPassword} avisa de que solo puede cambiar su
 * contraseña antes de operar.
 *
 * <p>{@code nombreCompleto} es el del trabajador vinculado, que es quien firma
 * sus registros clínicos, para que el cliente pueda mostrarlo sin otra petición.
 * Es {@code null} si el usuario no tiene trabajador (el ADMIN) y también si su
 * trabajador ya no existe: el vínculo sobrevive a la baja del trabajador, que
 * deja al usuario inactivo.</p>
 */
@Schema(description = "Usuario de acceso, sin contraseñas ni datos internos del token.")
public record UsuarioResponse(
        @Schema(description = "ID del usuario.", example = "USR-0001")
        String idUsuario,

        @Schema(description = "Nombre de usuario.", example = "ana.lopez")
        String username,

        @Schema(description = "Rol del usuario.", example = "DOCTOR",
                allowableValues = {"ADMIN", "DOCTOR", "ENFERMERO"})
        String rol,

        @Schema(description = "ID del trabajador vinculado, o `null` si no tiene.", example = "DOC-0001")
        String idTrabajador,

        @Schema(description = "Nombre completo del trabajador vinculado, o `null` si no tiene.",
                example = "Ana López")
        String nombreCompleto,

        @Schema(description = "`true` si el usuario puede iniciar sesión.", example = "true")
        boolean activo,

        @Schema(description = "`true` si debe cambiar la contraseña antes de operar.", example = "false")
        boolean debeCambiarPassword) {

    /** @param nombreCompleto nombre del trabajador vinculado, o {@code null} si no tiene */
    public static UsuarioResponse from(Usuario usuario, String nombreCompleto) {
        return new UsuarioResponse(usuario.getIdUsuario(), usuario.getUsername(),
                usuario.getRol().name(), usuario.getIdTrabajador(), nombreCompleto,
                usuario.isActivo(), usuario.isDebeCambiarPassword());
    }
}
