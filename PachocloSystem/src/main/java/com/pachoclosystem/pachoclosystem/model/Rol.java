package com.pachoclosystem.pachoclosystem.model;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Roles que un usuario puede tener en la API.
 * Los permisos de cada rol están en {@code SecurityConfig} (tabla "Permisos por
 * rol" del README) y en las reglas de negocio de los servicios.
 */
@Schema(description = "Rol de un usuario de acceso.",
        allowableValues = {"ADMIN", "DOCTOR", "ENFERMERO"})
public enum Rol {
    ADMIN,
    DOCTOR,
    ENFERMERO
}
