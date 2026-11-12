package com.pachoclosystem.medicamentos.model;

import io.swagger.v3.oas.annotations.media.Schema;

/** Forma farmacéutica del medicamento. */
@Schema(description = "Forma farmacéutica.",
        allowableValues = {"TABLETA", "CAPSULA", "JARABE", "SUSPENSION", "INYECTABLE",
                "CREMA", "GOTAS", "OTRO"})
public enum Presentacion {
    TABLETA,
    CAPSULA,
    JARABE,
    SUSPENSION,
    INYECTABLE,
    CREMA,
    GOTAS,
    OTRO
}
