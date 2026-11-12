package com.pachoclosystem.pachoclosystem.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Tipo de registro clínico. */
@Schema(description = "Tipo de registro clínico.",
        allowableValues = {"DIAGNOSTICO", "EVOLUCION", "MEDICACION", "SIGNOS_VITALES"})
public enum TipoRegistro {
    DIAGNOSTICO,
    EVOLUCION,
    MEDICACION,
    SIGNOS_VITALES
}
