package com.pachoclosystem.pachoclosystem.controller;

import com.pachoclosystem.pachoclosystem.dto.RegistroResponse;
import com.pachoclosystem.pachoclosystem.model.Rol;
import com.pachoclosystem.pachoclosystem.repository.HistorialIdempotenciaRepositoryMongo;
import com.pachoclosystem.pachoclosystem.repository.IHistorialIdempotenciaRepository;
import com.pachoclosystem.pachoclosystem.service.AlmacenIdempotenciaHistorial;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.notNull;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Registros clínicos y claves de idempotencia guardados en MongoDB: la copia
 * del autor, la fecha exacta, la caducidad de las claves y la atomicidad entre el
 * registro y su clave.
 */
class HistorialPersistenciaTest extends MockMvcBaseTest {

    private static final String EVOLUCION = "{\"tipo\":\"EVOLUCION\",\"contenido\":\"Paciente estable\"}";

    @MockitoSpyBean
    private HistorialIdempotenciaRepositoryMongo repositorioIdempotencia;

    private ResultActions postConClave(String trabajador, String paciente, String clave) throws Exception {
        return mockMvc.perform(post("/api/pacientes/{id}/historial", paciente)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenDeTrabajador(trabajador))
                .header("Idempotency-Key", clave)
                .contentType(MediaType.APPLICATION_JSON)
                .content(EVOLUCION));
    }

    private static String cuerpo(MvcResult resultado) {
        return new String(resultado.getResponse().getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void elRegistroConservaElAutorTalComoFirmoAunqueSeEditeOElimineElTrabajador() throws Exception {
        String paciente = registrarPaciente("Ana Torres", 30, 101);
        String doctor = registrarDoctor("Carlos Mena", "Cardiologia");
        registrarRegistro(paciente, Rol.DOCTOR, doctor, EVOLUCION);

        perform(put("/api/trabajadores/{id}", doctor)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Carlos Mena Ruiz\",\"rol\":\"Doctor\",\"especialidad\":\"Neurologia\"}"))
                .andExpect(status().isOk());

        perform(get("/api/pacientes/{id}/historial", paciente))
                .andExpect(jsonPath("$[0].autor.idTrabajador").value(doctor))
                .andExpect(jsonPath("$[0].autor.nombreCompleto").value("Carlos Mena"))
                .andExpect(jsonPath("$[0].autor.especialidad").value("Cardiologia"));

        perform(delete("/api/trabajadores/{id}", doctor)).andExpect(status().isNoContent());

        perform(get("/api/historial"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].autor.nombreCompleto").value("Carlos Mena"))
                .andExpect(jsonPath("$[0].autor.rol").value("Doctor"));
    }

    @Test
    void laFechaDelAltaEsLaMismaQueSeLeeDespues() throws Exception {
        String paciente = registrarPaciente("Ana Torres", 30, 101);
        String doctor = registrarDoctor("Carlos Mena", "Cardiologia");

        MvcResult alta = postHistorialComo(doctor, paciente, EVOLUCION).andExpect(status().isCreated()).andReturn();
        String fecha = leer(alta, "$.fecha");

        perform(get("/api/pacientes/{id}/historial", paciente)).andExpect(jsonPath("$[0].fecha").value(fecha));
        perform(get("/api/historial")).andExpect(jsonPath("$[0].fecha").value(fecha));
    }

    @Test
    void siNoSePuedeGuardarLaClaveTampocoQuedaElRegistro() throws Exception {
        String paciente = registrarPaciente("Ana Torres", 30, 101);
        String doctor = registrarDoctor("Carlos Mena", "Cardiologia");
        String clave = UUID.randomUUID().toString();
        // Falla solo al guardar la clave completada (con registro), dentro de la transacción del registro.
        doThrow(new IllegalStateException("fallo simulado al guardar la clave"))
                .when(repositorioIdempotencia)
                .guardar(eq(clave), anyString(), anyString(), anyString(), notNull(), any());

        postConClave(doctor, paciente, clave).andExpect(status().isInternalServerError());

        perform(get("/api/pacientes/{id}/historial", paciente)).andExpect(jsonPath("$", hasSize(0)));
        // La clave quedó ligada a la petición como incierta: repetirla vuelve a intentarlo.
        IHistorialIdempotenciaRepository.UsoGuardado incierto =
                repositorioIdempotencia.buscarVigente(clave, Instant.EPOCH).orElseThrow();
        assertThat(incierto.registro()).isNull();

        doCallRealMethod().when(repositorioIdempotencia)
                .guardar(eq(clave), anyString(), anyString(), anyString(), notNull(), any());
        MvcResult creado = postConClave(doctor, paciente, clave).andExpect(status().isCreated()).andReturn();
        postConClave(doctor, paciente, clave)
                .andExpect(status().isCreated())
                .andExpect(header().string("Idempotency-Replayed", "true"));

        perform(get("/api/pacientes/{id}/historial", paciente)).andExpect(jsonPath("$", hasSize(1)));
        assertThat(repositorioIdempotencia.buscarVigente(clave, Instant.EPOCH).orElseThrow().registro().idRegistro())
                .isEqualTo(leer(creado, "$.idRegistro"));
    }

    @Test
    void laRepeticionDevuelveExactamenteElMismoJson() throws Exception {
        String paciente = registrarPaciente("Ana Torres", 30, 101);
        String doctor = registrarDoctor("Carlos Mena", "Cardiologia");
        String clave = UUID.randomUUID().toString();

        MvcResult primera = postConClave(doctor, paciente, clave).andExpect(status().isCreated()).andReturn();
        MvcResult segunda = postConClave(doctor, paciente, clave)
                .andExpect(header().string("Idempotency-Replayed", "true")).andReturn();

        assertThat(cuerpo(segunda)).isEqualTo(cuerpo(primera));
    }

    @Test
    void unaClaveGuardadaEnMongoCaducaALas24Horas() {
        Instant inicio = Instant.parse("2026-06-15T10:00:00Z");
        RegistroResponse registro = new RegistroResponse("reg-1", "PAC-0001", "Ana Torres", null,
                null, null, "Paciente estable", null);
        AlmacenIdempotenciaHistorial alGuardar = new AlmacenIdempotenciaHistorial(repositorioIdempotencia,
                Clock.fixed(inicio, ZoneOffset.UTC), Duration.ofHours(24));
        alGuardar.guardar("clave-caduca-00000", "USR-0001", "PAC-0001", "h".repeat(64), registro);

        AlmacenIdempotenciaHistorial casiUnDia = new AlmacenIdempotenciaHistorial(repositorioIdempotencia,
                Clock.fixed(inicio.plus(Duration.ofHours(24)).minusMillis(1), ZoneOffset.UTC), Duration.ofHours(24));
        assertThat(casiUnDia.buscar("clave-caduca-00000")).hasValueSatisfying(
                uso -> assertThat(uso.registro()).isEqualTo(registro));

        AlmacenIdempotenciaHistorial unDia = new AlmacenIdempotenciaHistorial(repositorioIdempotencia,
                Clock.fixed(inicio.plus(Duration.ofHours(24)), ZoneOffset.UTC), Duration.ofHours(24));
        assertThat(unDia.buscar("clave-caduca-00000")).isEmpty();
        assertThat(unDia.tamano()).isZero();

        // Al guardar otra clave se purga la caducada.
        unDia.guardar("otra-clave-0000000", "USR-0001", "PAC-0001", "h".repeat(64), null);
        assertThat(mongoTemplate.count(new org.springframework.data.mongodb.core.query.Query(), "historial_idempotencia")).isEqualTo(1);
    }
}
