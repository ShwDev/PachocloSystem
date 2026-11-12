package com.pachoclosystem.medicamentos.controller;

import com.jayway.jsonpath.JsonPath;
import com.pachoclosystem.medicamentos.MongoTestBase;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base común de los tests MockMvc: la aplicación completa contra el MongoDB de
 * pruebas, con la fecha fijada en {@link #HOY} y la base vacía antes de cada test
 * (ver {@link MongoTestBase}), de modo que cada prueba es independiente del
 * orden de ejecución.
 */
@AutoConfigureMockMvc
abstract class MockMvcBaseTest extends MongoTestBase {

    @Autowired
    protected MockMvc mockMvc;

    /** Lee un valor del cuerpo de la respuesta (siempre en UTF-8). */
    protected <T> T leer(MvcResult resultado, String expresion) throws Exception {
        return JsonPath.read(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8), expresion);
    }

    /** Cuerpo JSON válido de alta, variando lo que suelen cambiar las pruebas. */
    protected static String cuerpoAlta(String nombre, String lote, int stock, int stockMinimo, LocalDate vencimiento) {
        return """
                {"nombre":"%s","principioActivo":"Paracetamol","presentacion":"TABLETA",
                 "concentracion":"500 mg","laboratorio":"Genfar","lote":"%s",
                 "cantidadStock":%d,"stockMinimo":%d,"fechaVencimiento":"%s",
                 "ubicacion":"Farmacia - Estante A3"}
                """.formatted(nombre, lote, stock, stockMinimo, vencimiento);
    }

    /** Registra un medicamento válido y devuelve su ID (p. ej. MED-0001). */
    protected String registrar(String nombre, String lote, int stock, int stockMinimo, LocalDate vencimiento)
            throws Exception {
        MvcResult resultado = mockMvc.perform(post("/api/medicamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpoAlta(nombre, lote, stock, stockMinimo, vencimiento)))
                .andExpect(status().isCreated())
                .andReturn();
        return leer(resultado, "$.idMedicamento");
    }
}
