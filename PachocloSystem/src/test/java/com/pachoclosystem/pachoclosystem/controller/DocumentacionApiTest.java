package com.pachoclosystem.pachoclosystem.controller;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba que la documentación OpenAPI se publica y describe la API real:
 * las rutas principales y el esquema JWT, que el login desactiva. Verifica
 * además que Swagger UI es pública, porque {@code SecurityConfig} la excluye de
 * la autenticación.
 */
class DocumentacionApiTest extends MockMvcBaseTest {

    @Test
    void apiDocsPublicaRutasYEsquemaBearer() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("PachocloSystem API"))
                .andExpect(jsonPath("$.paths['/api/auth/login'].post").exists())
                .andExpect(jsonPath("$.paths['/api/auth/me'].get").exists())
                .andExpect(jsonPath("$.paths['/api/pacientes'].get").exists())
                .andExpect(jsonPath("$.paths['/api/pacientes/{id}/historial'].post").exists())
                .andExpect(jsonPath("$.paths['/api/historial'].get").exists())
                .andExpect(jsonPath("$.paths['/api/trabajadores'].get").exists())
                .andExpect(jsonPath("$.paths['/api/usuarios'].get").exists())
                .andExpect(jsonPath("$.paths['/api/medicamentos/{id}/salidas'].post").exists())
                .andExpect(jsonPath("$.components.schemas.LoginRequest").exists())
                .andExpect(jsonPath("$.components.schemas.RegistroRequest").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.type").value("http"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.bearerFormat").value("JWT"))
                .andExpect(jsonPath("$.tags[*].name", hasItem("Autenticación")));
    }

    @Test
    void elLoginNoExigeTokenEnLaDocumentacion() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                // El resto de operaciones heredan el requisito global bearerAuth.
                .andExpect(jsonPath("$.security[0].bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/auth/login'].post.security").isEmpty());
    }

    @Test
    void lasRutasIdempotentesDocumentanSuCabecera() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/pacientes/{id}/historial'].post.parameters[?(@.name == 'Idempotency-Key')]")
                        .exists());
    }

    @Test
    void elDeleteDePacientesSeDocumentaComoBajaLogica() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/pacientes/{id}'].delete.summary",
                        containsString("baja lógica")))
                .andExpect(jsonPath("$.paths['/api/pacientes/{id}'].delete.description",
                        allOf(containsString("activo=false"), containsString("No borra el documento"),
                                containsString("se conservan"))))
                .andExpect(jsonPath("$.paths['/api/pacientes/{id}'].delete.responses['204'].description",
                        containsString("activo=false")))
                // Trabajadores y medicamentos sí borran el documento, y así lo dicen.
                .andExpect(jsonPath("$.paths['/api/trabajadores/{id}'].delete.description",
                        containsString("Borrado físico")))
                .andExpect(jsonPath("$.paths['/api/medicamentos/{id}'].delete.description",
                        containsString("borrado físico")));
    }

    @Test
    void historialSinPutNiDeleteYUsuariosSinDeleteSonIntencionales() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/pacientes/{id}/historial'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/pacientes/{id}/historial'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/historial'].put").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/historial'].delete").doesNotExist())
                .andExpect(jsonPath("$.paths['/api/usuarios/{id}'].delete").doesNotExist())
                .andExpect(jsonPath("$.tags[?(@.name == 'Historial clínico')].description",
                        hasItem(containsString("Sin PUT ni DELETE a propósito"))))
                .andExpect(jsonPath("$.tags[?(@.name == 'Usuarios')].description",
                        hasItem(containsString("Sin DELETE a propósito"))));
    }

    @Test
    void lasOperacionesDeEscrituraDocumentanCuerpoYCodigos() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/pacientes'].post.requestBody.content['application/json'].schema['$ref']",
                        containsString("PacienteRequest")))
                .andExpect(jsonPath("$.paths['/api/pacientes/{id}'].put.requestBody").exists())
                .andExpect(jsonPath("$.paths['/api/pacientes/{id}/habitacion'].patch.requestBody").exists())
                .andExpect(jsonPath("$.paths['/api/trabajadores'].post.requestBody").exists())
                .andExpect(jsonPath("$.paths['/api/trabajadores/{id}'].put.requestBody").exists())
                .andExpect(jsonPath("$.paths['/api/medicamentos'].post.requestBody").exists())
                .andExpect(jsonPath("$.paths['/api/medicamentos/{id}'].put.requestBody").exists())
                .andExpect(jsonPath("$.paths['/api/pacientes'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/trabajadores'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/medicamentos'].post.responses['201']").exists())
                .andExpect(jsonPath("$.paths['/api/pacientes/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/trabajadores/{id}'].delete.responses['204']").exists())
                .andExpect(jsonPath("$.paths['/api/medicamentos/{id}'].delete.responses['204']").exists());
    }

    @Test
    void swaggerUiEsPublica() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
