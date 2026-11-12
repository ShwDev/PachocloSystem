package com.pachoclosystem.pachoclosystem.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Nivel de experiencia de un enfermero. */
@Schema(description = "Nivel de experiencia de un enfermero.",
        allowableValues = {"NOVATO", "PRINCIPIANTE", "AVANZADO"})
public enum NivelExperiencia {
    NOVATO,
    PRINCIPIANTE,
    AVANZADO
}
