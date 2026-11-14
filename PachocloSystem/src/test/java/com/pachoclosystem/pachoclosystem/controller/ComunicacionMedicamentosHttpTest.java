package com.pachoclosystem.pachoclosystem.controller;

import com.pachoclosystem.pachoclosystem.model.Rol;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Comunicación PachocloSystem → MedicamentosService de extremo a extremo: la
 * petición entra por la API (con el token JWT del usuario), pasa por el cliente
 * HTTP real de la aplicación y llega por la red a un servidor HTTP que imita a
 * MedicamentosService. Comprueba lo que viaja por el cable (la cabecera
 * {@code X-Api-Key}, la ruta y que el token del usuario <em>no</em> se reenvía) y
 * cómo se traducen los errores del microservicio para el usuario final:
 *
 * <ul>
 *   <li>401 (clave rechazada) y 5xx del microservicio → <b>502</b>, sin filtrar detalles;</li>
 *   <li>microservicio que corta la conexión sin responder → <b>503</b>;</li>
 *   <li>404 del microservicio → 404 con su mismo mensaje.</li>
 * </ul>
 *
 * <p>Los tests unitarios del cliente ({@code MedicamentosClientTest}) y del
 * controlador ({@code MedicamentoControllerTest}) cubren cada capa por separado;
 * este las une con HTTP real.</p>
 */
class ComunicacionMedicamentosHttpTest extends MockMvcBaseTest {

    private static final String CLAVE = "clave-servicio-e2e-0123456789";
    private static final String MENSAJE_RESPUESTA_INESPERADA =
            "El servicio de medicamentos respondió de forma inesperada.";
    private static final String MENSAJE_NO_DISPONIBLE =
            "El servicio de medicamentos no está disponible. Vuelva a intentarlo más tarde.";
    private static final String MEDICAMENTO_JSON = """
            {"idMedicamento":"MED-0001","nombre":"Dolex","principioActivo":"Paracetamol",
             "presentacion":"TABLETA","concentracion":"500 mg","laboratorio":"GSK","lote":"L-1",
             "cantidadStock":10,"stockMinimo":5,"fechaVencimiento":"2027-03-31",
             "ubicacion":"Estante A3","stockBajo":false,"vencido":false}""";

    private static final Stub STUB = Stub.arrancar();

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registro) {
        registro.add("medicamentos.url", STUB::url);
        registro.add("medicamentos.api-key", () -> CLAVE);
    }

    @AfterAll
    static void pararStub() {
        STUB.parar();
    }

    @BeforeEach
    void reiniciarStub() {
        STUB.reiniciar();
    }

    private static String error(int status, String error, String mensaje) {
        return "{\"status\":" + status + ",\"error\":\"" + error + "\",\"mensajes\":[\"" + mensaje + "\"]}";
    }

    // --- Lo que viaja por la red ------------------------------------------

    @Test
    void laPeticionLlegaConLaClaveDeServicioYSinElTokenDelUsuario() throws Exception {
        STUB.responder(200, "[" + MEDICAMENTO_JSON + "]");

        performComo(Rol.DOCTOR, get("/api/medicamentos").param("q", "dolex"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].idMedicamento").value("MED-0001"))
                .andExpect(jsonPath("$[0].cantidadStock").value(10));

        assertThat(STUB.peticiones()).singleElement().satisfies(peticion -> {
            assertThat(peticion.metodo()).isEqualTo("GET");
            assertThat(peticion.ruta()).isEqualTo("/api/medicamentos");
            assertThat(peticion.consulta()).isEqualTo("q=dolex");
            assertThat(peticion.apiKey()).isEqualTo(CLAVE);
            // El JWT del usuario se queda en PachocloSystem: el microservicio solo ve la clave.
            assertThat(peticion.authorization()).isNull();
        });
    }

    @Test
    void altaYBajaViajanConLaClaveYDevuelvenElCodigoDelMicroservicio() throws Exception {
        STUB.responder(201, MEDICAMENTO_JSON);
        perform(post("/api/medicamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nombre":"Dolex","principioActivo":"Paracetamol","presentacion":"TABLETA",
                                 "concentracion":"500 mg","laboratorio":"GSK","lote":"L-1","cantidadStock":10,
                                 "stockMinimo":5,"fechaVencimiento":"2027-03-31","ubicacion":"Estante A3"}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.idMedicamento").value("MED-0001"));

        STUB.responder(204, null);
        perform(delete("/api/medicamentos/{id}", "MED-0001"))
                .andExpect(status().isNoContent());

        assertThat(STUB.peticiones()).extracting(Peticion::metodo, Peticion::ruta, Peticion::apiKey)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("POST", "/api/medicamentos", CLAVE),
                        org.assertj.core.groups.Tuple.tuple("DELETE", "/api/medicamentos/MED-0001", CLAVE));
    }

    // --- Errores del microservicio ------------------------------------------

    @Test
    void claveRechazadaPorElMicroservicioDevuelve502SinFiltrarDetalles() throws Exception {
        STUB.responder(401, error(401, "Unauthorized", "Falta la clave de servicio o no es válida."));

        perform(get("/api/medicamentos"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value(502))
                .andExpect(jsonPath("$.mensajes", contains(MENSAJE_RESPUESTA_INESPERADA)))
                // El usuario no debe enterarse de que existe una clave interna.
                .andExpect(content().string(not(containsString("clave"))));
        assertThat(STUB.peticiones()).hasSize(1);
    }

    @Test
    void errorInternoDelMicroservicioDevuelve502() throws Exception {
        STUB.responder(500, error(500, "Internal Server Error", "Se produjo un error interno."));

        perform(get("/api/medicamentos/{id}", "MED-0001"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensajes", contains(MENSAJE_RESPUESTA_INESPERADA)));
    }

    @Test
    void baseCaidaEnElMicroservicioDevuelve502() throws Exception {
        // MedicamentosService responde 503 si su MongoDB no está disponible; la pasarela
        // no reenvía ese código tal cual sino que lo trata como respuesta inesperada.
        STUB.responder(503, error(503, "Service Unavailable", "La base de datos no está disponible."));

        perform(get("/api/medicamentos"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.mensajes", contains(MENSAJE_RESPUESTA_INESPERADA)));
    }

    @Test
    void microservicioQueCortaLaConexionDevuelve503() throws Exception {
        STUB.cortarConexion();

        perform(get("/api/medicamentos"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.mensajes", contains(MENSAJE_NO_DISPONIBLE)));
    }

    @Test
    void noEncontradoEnElMicroservicioLlegaComo404ConSuMensaje() throws Exception {
        STUB.responder(404, error(404, "Not Found", "No se encontró el medicamento MED-0404."));

        perform(get("/api/medicamentos/{id}", "MED-0404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.mensajes", contains("No se encontró el medicamento MED-0404.")));
    }

    // --- Servidor HTTP que imita a MedicamentosService ------------------------

    record Peticion(String metodo, String ruta, String consulta, String apiKey, String authorization) {
    }

    /** Servidor HTTP mínimo: registra cada petición y responde lo que se le haya programado. */
    static final class Stub {

        private final HttpServer servidor;
        private final Queue<Peticion> recibidas = new ConcurrentLinkedQueue<>();
        private volatile int estado = 200;
        private volatile String cuerpo = "[]";
        private volatile boolean cortar;

        private Stub(HttpServer servidor) {
            this.servidor = servidor;
        }

        static Stub arrancar() {
            try {
                HttpServer servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
                Stub stub = new Stub(servidor);
                servidor.createContext("/", stub::atender);
                servidor.start();
                return stub;
            } catch (IOException e) {
                throw new IllegalStateException("No se pudo arrancar el stub de MedicamentosService", e);
            }
        }

        String url() {
            return "http://127.0.0.1:" + servidor.getAddress().getPort();
        }

        void reiniciar() {
            recibidas.clear();
            responder(200, "[]");
        }

        void responder(int nuevoEstado, String nuevoCuerpo) {
            estado = nuevoEstado;
            cuerpo = nuevoCuerpo;
            cortar = false;
        }

        void cortarConexion() {
            cortar = true;
        }

        List<Peticion> peticiones() {
            return List.copyOf(recibidas);
        }

        void parar() {
            servidor.stop(0);
        }

        private void atender(HttpExchange intercambio) throws IOException {
            recibidas.add(new Peticion(
                    intercambio.getRequestMethod(),
                    intercambio.getRequestURI().getPath(),
                    intercambio.getRequestURI().getQuery(),
                    intercambio.getRequestHeaders().getFirst("X-Api-Key"),
                    intercambio.getRequestHeaders().getFirst("Authorization")));
            intercambio.getRequestBody().readAllBytes();
            if (cortar) {
                // Cierra sin enviar ni las cabeceras: para el cliente es un fallo de E/S.
                intercambio.close();
                return;
            }
            if (cuerpo == null) {
                intercambio.sendResponseHeaders(estado, -1);
                intercambio.close();
                return;
            }
            byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().set("Content-Type", "application/json");
            intercambio.sendResponseHeaders(estado, bytes.length);
            try (OutputStream salida = intercambio.getResponseBody()) {
                salida.write(bytes);
            }
        }
    }
}
