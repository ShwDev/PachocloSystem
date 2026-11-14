package com.pachoclosystem.pachoclosystem.controller;

import com.pachoclosystem.pachoclosystem.model.Paciente;
import com.pachoclosystem.pachoclosystem.model.Rol;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Baja lógica de pacientes: el DELETE (solo ADMIN) marca al paciente con
 * {@code activo=false}, pero el documento y su historial se conservan en MongoDB
 * (decisión de diseño: en un hospital no se elimina información clínica). Un
 * paciente dado de baja se comporta como inexistente (404) y no se reactiva.
 */
class PacienteSoftDeleteTest extends MockMvcBaseTest {

    @Test
    void elAdminDaDeBajaUnPacienteYEsteDejaDeSerVisible() throws Exception {
        String id = registrarPaciente("Ana Torres", 30, 101);

        performComo(Rol.DOCTOR, get("/api/pacientes/{id}", id))
                .andExpect(status().isOk());

        perform(delete("/api/pacientes/{id}", id))
                .andExpect(status().isNoContent());

        performComo(Rol.DOCTOR, get("/api/pacientes/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.mensajes[0]").value("No se encontró el paciente " + id + "."));

        performComo(Rol.DOCTOR, get("/api/pacientes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // El paciente sigue en la base, solo marcado como inactivo.
        Paciente paciente = repositorioPacientes.buscarPorId(id);
        assertThat(paciente).isNotNull();
        assertThat(paciente.isActivo()).isFalse();
    }

    @Test
    void unPacienteDadoDeBajaNoSePuedeEditarNiCambiarDeHabitacion() throws Exception {
        String id = registrarPaciente("Ana Torres", 30, 101);
        perform(delete("/api/pacientes/{id}", id)).andExpect(status().isNoContent());

        performComo(Rol.DOCTOR, put("/api/pacientes/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Ana Maria Torres\",\"edad\":31,\"habitacion\":205}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensajes[0]").value("No se encontró el paciente " + id + "."));

        performComo(Rol.DOCTOR, patch("/api/pacientes/{id}/habitacion", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"habitacion\":205}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void noSePuedeDarDeBajaDosVecesAlMismoPaciente() throws Exception {
        String id = registrarPaciente("Ana Torres", 30, 101);

        perform(delete("/api/pacientes/{id}", id)).andExpect(status().isNoContent());
        perform(delete("/api/pacientes/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensajes[0]").value("No se encontró el paciente " + id + "."));
    }

    @Test
    void laBusquedaNoDevuelvePacientesDadosDeBaja() throws Exception {
        registrarPaciente("Ana Torres", 30, 101);
        String dadoDeBaja = registrarPaciente("Bruno Diaz", 45, 202);
        perform(delete("/api/pacientes/{id}", dadoDeBaja)).andExpect(status().isNoContent());

        performComo(Rol.DOCTOR, get("/api/pacientes").param("q", "bruno"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void elHistorialDeUnPacienteDadoDeBajaSeVuelveInaccesibleYElGlobalLoExcluye() throws Exception {
        String activo = registrarPaciente("Ana Torres", 30, 101);
        String dadoDeBaja = registrarPaciente("Bruno Diaz", 45, 202);
        String doctor = registrarDoctor("Carlos Mena", "Cardiologia");
        registrarRegistro(dadoDeBaja, Rol.DOCTOR, doctor,
                "{\"tipo\":\"DIAGNOSTICO\",\"contenido\":\"Cuadro inicial\"}");
        registrarRegistro(activo, Rol.DOCTOR, doctor,
                "{\"tipo\":\"DIAGNOSTICO\",\"contenido\":\"Hipertension leve\"}");

        perform(delete("/api/pacientes/{id}", dadoDeBaja)).andExpect(status().isNoContent());

        performComo(Rol.DOCTOR, get("/api/pacientes/{id}/historial", dadoDeBaja))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensajes[0]").value("No se encontró el paciente " + dadoDeBaja + "."));

        performComo(Rol.DOCTOR, get("/api/historial"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idPaciente").value(activo));
    }

    @Test
    void elDocumentoSigueEnMongoConActivoFalseYConservaSuHistorial() throws Exception {
        String id = registrarPaciente("Ana Torres", 30, 101);
        String doctor = registrarDoctor("Carlos Mena", "Cardiologia");
        registrarRegistro(id, Rol.DOCTOR, doctor,
                "{\"tipo\":\"DIAGNOSTICO\",\"contenido\":\"Cuadro inicial\"}");
        registrarRegistro(id, Rol.DOCTOR, doctor,
                "{\"tipo\":\"EVOLUCION\",\"contenido\":\"Mejora progresiva\"}");
        Query registrosDelPaciente = new Query(Criteria.where("idPaciente").is(id));
        assertThat(mongoTemplate.count(registrosDelPaciente, "registros_clinicos")).isEqualTo(2);

        perform(delete("/api/pacientes/{id}", id)).andExpect(status().isNoContent());

        // Deja de salir en las consultas de la API...
        performComo(Rol.DOCTOR, get("/api/pacientes")).andExpect(jsonPath("$", hasSize(0)));
        performComo(Rol.DOCTOR, get("/api/pacientes/{id}", id)).andExpect(status().isNotFound());

        // ...pero el documento crudo sigue en la colección, con sus datos y activo=false.
        assertThat(mongoTemplate.count(new Query(), "pacientes")).isEqualTo(1);
        Document documento = mongoTemplate.findById(id, Document.class, "pacientes");
        assertThat(documento).isNotNull();
        assertThat(documento.getBoolean("activo")).isFalse();
        assertThat(documento.getString("nombre")).isEqualTo("Ana Torres");
        assertThat(documento.getInteger("habitacion")).isEqualTo(101);

        // Ningún borrado en cascada: los registros clínicos siguen intactos.
        assertThat(mongoTemplate.count(registrosDelPaciente, "registros_clinicos")).isEqualTo(2);
        assertThat(mongoTemplate.find(registrosDelPaciente, Document.class, "registros_clinicos"))
                .extracting(registro -> registro.getString("contenido"))
                .containsExactlyInAnyOrder("Cuadro inicial", "Mejora progresiva");
    }
}
