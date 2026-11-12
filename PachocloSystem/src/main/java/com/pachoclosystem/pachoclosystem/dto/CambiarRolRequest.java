package com.pachoclosystem.pachoclosystem.dto;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Cambio de rol (y del trabajador vinculado) decidido por un administrador.
 */
@Schema(description = "Nuevo rol del usuario y, si aplica, el trabajador al que queda vinculado.")
public record CambiarRolRequest(
        @Schema(description = "Rol nuevo.", example = "ENFERMERO",
                allowableValues = {"ADMIN", "DOCTOR", "ENFERMERO"})
        String rol,

        @Schema(description = "ID del trabajador vinculado. Obligatorio para DOCTOR y ENFERMERO; "
                + "se omite para ADMIN.", example = "ENF-0001")
        String idTrabajador) {
}
