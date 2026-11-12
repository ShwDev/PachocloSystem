package com.pachoclosystem.medicamentos.controller;

import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comprueba que la documentación OpenAPI se publica y describe la API real:
 * todas las rutas de {@code /api/medicamentos} y la autenticación por
 * {@code X-Api-Key}. También verifica que Swagger UI es accesible sin la clave
 * del servicio, porque el filtro solo cubre {@code /api/**}.
 */
class DocumentacionApiTest extends MockMvcBaseTest {

    @Test
    void apiDocsPublicaRutasYClaveDeServicio() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.info.title").value("PachocloSystem · MedicamentosService"))
                .andExpect(jsonPath("$.paths['/api/medicamentos'].get").exists())
                .andExpect(jsonPath("$.paths['/api/medicamentos'].post").exists())
                .andExpect(jsonPath("$.paths['/api/medicamentos/{id}/salidas'].post").exists())
                .andExpect(jsonPath("$.paths['/api/medicamentos/stock-bajo'].get").exists())
                .andExpect(jsonPath("$.paths['/api/medicamentos/por-vencer'].get").exists())
                .andExpect(jsonPath("$.paths['/api/medicamentos/vencidos'].get").exists())
                .andExpect(jsonPath("$.components.schemas.CrearMedicamentoRequest").exists())
                .andExpect(jsonPath("$.components.schemas.MedicamentoResponse").exists())
                .andExpect(jsonPath("$.components.schemas.ErrorResponse").exists())
                .andExpect(jsonPath("$.components.securitySchemes.claveServicio.type").value("apiKey"))
                .andExpect(jsonPath("$.components.securitySchemes.claveServicio.in").value("header"))
                .andExpect(jsonPath("$.components.securitySchemes.claveServicio.name")
                        .value("X-Api-Key"))
                .andExpect(jsonPath("$.tags[0].name").value("Medicamentos"));
    }

    @Test
    void laSalidaDocumentaLaCabeceraDeIdempotencia() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paths['/api/medicamentos/{id}/salidas'].post.parameters[?(@.name == 'Idempotency-Key')]")
                        .exists());
    }

    @Test
    void swaggerUiSePublicaSinExigirLaClave() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", containsString("/swagger-ui/index.html")));

        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk());
    }
}
