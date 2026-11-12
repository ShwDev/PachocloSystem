package com.pachoclosystem.pachoclosystem.dto;

import com.pachoclosystem.pachoclosystem.model.TipoRegistro;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/**
 * Para SIGNOS_VITALES se envían {@code signosVitales}; para los demás tipos, {@code contenido}.
 *
 * <p>Opcional, solo para MEDICACION: {@code idMedicamento} y {@code cantidad} (los dos
 * juntos) descuentan ese stock en MedicamentosService al crear el registro.</p>
 */
@Schema(description = "Registro clínico nuevo. El autor es siempre el trabajador del usuario autenticado.")
public record RegistroRequest(
        @Schema(description = "Tipo de registro.", example = "SIGNOS_VITALES",
                allowableValues = {"DIAGNOSTICO", "EVOLUCION", "MEDICACION", "SIGNOS_VITALES"})
        @NotNull(message = "Debe seleccionar un tipo de registro.")
        TipoRegistro tipo,

        // Opcional: el autor es siempre el trabajador del usuario autenticado. Si
        // viene, debe coincidir con él (si no, 400). Se acepta por compatibilidad.
        @Schema(description = "ID del autor. Opcional: si viene, debe coincidir con el trabajador "
                + "del usuario autenticado.", example = "DOC-0001")
        @Pattern(regexp = "^[A-Za-z0-9\\-]{1,20}$",
                message = "El ID del autor solo puede tener letras, números y guiones.")
        String idAutor,

        @Schema(description = "Texto libre del registro. Obligatorio salvo en SIGNOS_VITALES.",
                example = "Paciente refiere dolor leve en el pecho.")
        String contenido,

        @Schema(description = "Signos vitales. Obligatorio en SIGNOS_VITALES.")
        @Valid
        SignosVitalesRequest signosVitales,

        @Schema(description = "Medicamento administrado. Solo para MEDICACION; debe ir junto con "
                + "`cantidad` para descontar stock.", example = "MED-0001")
        @Pattern(regexp = "^[A-Za-z0-9\\-]{1,20}$",
                message = "El ID del medicamento solo puede tener letras, números y guiones.")
        String idMedicamento,

        @Schema(description = "Unidades administradas. Solo para MEDICACION, junto con "
                + "`idMedicamento`.", example = "2", minimum = "1", maximum = "1000000")
        @Min(value = 1, message = "La cantidad administrada debe ser un entero entre 1 y 1000000.")
        @Max(value = 1_000_000, message = "La cantidad administrada debe ser un entero entre 1 y 1000000.")
        Integer cantidad) {
}
