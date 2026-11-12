package com.pachoclosystem.pachoclosystem.dto;

import com.pachoclosystem.pachoclosystem.model.AutorRegistro;
import com.pachoclosystem.pachoclosystem.model.Doctor;
import com.pachoclosystem.pachoclosystem.model.Enfermero;
import com.pachoclosystem.pachoclosystem.model.NivelExperiencia;
import com.pachoclosystem.pachoclosystem.model.TrabajadorHospital;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Doctor o enfermero del hospital.")
public record TrabajadorResponse(
        @Schema(description = "ID del trabajador.", example = "DOC-0001")
        String idTrabajador,

        @Schema(description = "Nombre completo.", example = "Carlos Ruiz")
        String nombreCompleto,

        @Schema(description = "Rol del trabajador.", example = "Doctor",
                allowableValues = {"Doctor", "Enfermero"})
        String rol,

        @Schema(description = "Especialidad, solo en doctores.", example = "Cardiología")
        String especialidad,

        @Schema(description = "Nivel de experiencia, solo en enfermeros.", example = "AVANZADO",
                allowableValues = {"NOVATO", "PRINCIPIANTE", "AVANZADO"})
        NivelExperiencia nivelExperiencia) {

    public static TrabajadorResponse from(TrabajadorHospital t) {
        if (t instanceof Doctor d) {
            return new TrabajadorResponse(d.getIdTrabajador(), d.getNombreCompleto(), "Doctor",
                    d.getEspecialidad(), null);
        }
        Enfermero e = (Enfermero) t;
        return new TrabajadorResponse(e.getIdTrabajador(), e.getNombreCompleto(), "Enfermero",
                null, e.getNivelExperiencia());
    }

    /** El autor de un registro clínico, con los datos que tenía al firmarlo. */
    public static TrabajadorResponse from(AutorRegistro a) {
        return new TrabajadorResponse(a.idTrabajador(), a.nombreCompleto(), a.rol(), a.especialidad(),
                a.nivelExperiencia());
    }
}
