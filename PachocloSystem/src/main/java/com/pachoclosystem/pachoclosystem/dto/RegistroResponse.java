package com.pachoclosystem.pachoclosystem.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.pachoclosystem.pachoclosystem.model.Paciente;
import com.pachoclosystem.pachoclosystem.model.RegistroClinico;
import com.pachoclosystem.pachoclosystem.model.TipoRegistro;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

/**
 * {@code medicacion} solo aparece en los registros de MEDICACION que descontaron
 * stock; en el resto no se serializa, así que esas respuestas no cambian.
 */
@Schema(description = "Registro clínico firmado por un trabajador.")
public record RegistroResponse(
        @Schema(description = "ID del registro.", example = "REG-0001")
        String idRegistro,

        @Schema(description = "ID del paciente.", example = "PAC-0001")
        String idPaciente,

        @Schema(description = "Nombre del paciente.", example = "Ana López")
        String nombrePaciente,

        @Schema(description = "Fecha y hora del registro.", example = "2026-11-12T09:30:00")
        LocalDateTime fecha,

        @Schema(description = "Tipo de registro.", example = "SIGNOS_VITALES",
                allowableValues = {"DIAGNOSTICO", "EVOLUCION", "MEDICACION", "SIGNOS_VITALES"})
        TipoRegistro tipo,

        @Schema(description = "Trabajador que firmó, con los datos que tenía al hacerlo.")
        TrabajadorResponse autor,

        @Schema(description = "Texto del registro.", example = "Paciente refiere dolor leve.")
        String contenido,

        @Schema(description = "Medicamento descontado del inventario. Solo en registros de "
                + "MEDICACION con stock.")
        @JsonInclude(JsonInclude.Include.NON_NULL) MedicacionResponse medicacion) {

    /** Medicamento y cantidad descontados del inventario por este registro. */
    @Schema(description = "Medicamento y cantidad descontados por el registro.")
    public record MedicacionResponse(
            @Schema(description = "ID del medicamento.", example = "MED-0001")
            String idMedicamento,

            @Schema(description = "Unidades descontadas.", example = "2")
            int cantidad) {
    }

    public static RegistroResponse from(Paciente p, RegistroClinico r) {
        return from(p.getIdPaciente(), p.getNombre(), r);
    }

    public static RegistroResponse from(String idPaciente, String nombrePaciente, RegistroClinico r) {
        MedicacionResponse medicacion = r.getIdMedicamento() == null
                ? null
                : new MedicacionResponse(r.getIdMedicamento(), r.getCantidad());
        return new RegistroResponse(r.getIdRegistro(), idPaciente, nombrePaciente,
                r.getFecha(), r.getTipo(), TrabajadorResponse.from(r.getAutor()), r.getContenido(),
                medicacion);
    }
}
