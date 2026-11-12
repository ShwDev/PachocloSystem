package com.pachoclosystem.pachoclosystem.controller;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import com.pachoclosystem.pachoclosystem.PachocloSystemApplication;
import com.pachoclosystem.pachoclosystem.config.AdminInicial;
import com.pachoclosystem.pachoclosystem.model.Usuario;
import com.pachoclosystem.pachoclosystem.repository.IUsuarioRepository;
import com.pachoclosystem.pachoclosystem.security.JwtTokenService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MongoDB cae con la aplicación ya en marcha: cada petición responde 503 con el
 * cuerpo de error uniforme y sin detalles internos. Con un token válido
 * también es 503, nunca 401: el frontend cerraría la sesión del usuario.
 *
 * <p>La caída se simula envolviendo el {@link MongoClient} con un proxy: mientras
 * {@link #CAIDA} está activa, cualquier operación contra la base falla como
 * cuando el driver no encuentra el servidor. Se envuelve el cliente y no la
 * fábrica de base de datos porque el chequeo de salud de Actuator usa el
 * {@code MongoClient} directamente: así también pasa a DOWN.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class BaseDeDatosNoDisponibleTest {

    private static final String MENSAJE_503 =
            "El servicio no puede acceder a sus datos en este momento. Vuelva a intentarlo más tarde.";

    /** Operaciones que nunca fallan: cerrar el cliente al apagar el contexto, etc. */
    private static final Set<String> SIEMPRE_PERMITIDAS = Set.of("close", "equals", "hashCode", "toString");

    private static volatile boolean CAIDA;

    @TestConfiguration
    static class BaseQueSePuedeCaer {

        @Bean
        static BeanPostProcessor envolverMongoClient() {
            return new BeanPostProcessor() {
                @Override
                public Object postProcessAfterInitialization(Object bean, String nombre) {
                    if (bean instanceof MongoClient original && !Proxy.isProxyClass(bean.getClass())) {
                        return Proxy.newProxyInstance(MongoClient.class.getClassLoader(),
                                new Class<?>[]{MongoClient.class}, (proxy, metodo, args) -> {
                                    if (CAIDA && !SIEMPRE_PERMITIDAS.contains(metodo.getName())) {
                                        throw new DataAccessResourceFailureException("Base de datos caída (simulada).");
                                    }
                                    try {
                                        return metodo.invoke(original, args);
                                    } catch (InvocationTargetException causa) {
                                        throw causa.getCause();
                                    }
                                });
                    }
                    return bean;
                }
            };
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private IUsuarioRepository repositorio;

    @Autowired
    private JwtTokenService tokenService;

    @Autowired
    private AdminInicial adminInicial;

    private String tokenAdmin;

    @BeforeEach
    void caerLaBase() {
        // Otro contexto de test pudo limpiar la base: el admin se crea si falta.
        if (!repositorio.existePorUsername("admin")) {
            adminInicial.run(new DefaultApplicationArguments());
        }
        Usuario admin = repositorio.buscarPorUsername("admin");
        tokenAdmin = tokenService.generarToken(admin);
        CAIDA = true;
    }

    @AfterEach
    void levantarLaBase() {
        CAIDA = false;
    }

    private static void es503Uniforme(ResultActions resultado) throws Exception {
        resultado.andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.mensajes[0]").value(MENSAJE_503))
                .andExpect(header().doesNotExist(HttpHeaders.WWW_AUTHENTICATE))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("mongodb"))));
    }

    @Test
    void unaPeticionConTokenValidoResponde503YNo401() throws Exception {
        es503Uniforme(mockMvc.perform(get("/api/pacientes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)));
        es503Uniforme(mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)));
    }

    @Test
    void elLoginSinBaseDeDatosResponde503() throws Exception {
        es503Uniforme(mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"pwd-de-prueba-solo-tests-12345\"}")));
    }

    @Test
    void elHealthCheckRespondeDownYLaPlataformaDejaDeEnviarTrafico() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
        // El proceso sigue vivo: no hay que reiniciarlo por una caída de la base.
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void sinTokenSigueSiendo401() throws Exception {
        mockMvc.perform(get("/api/pacientes")).andExpect(status().isUnauthorized());
    }

    @Test
    void alVolverLaBaseLaMismaPeticionFunciona() throws Exception {
        es503Uniforme(mockMvc.perform(get("/api/pacientes")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin)));
        CAIDA = false;
        mockMvc.perform(get("/api/pacientes").header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenAdmin))
                .andExpect(status().isOk());
    }

    @Test
    void sinBaseDeDatosLaAplicacionNoArranca() {
        CAIDA = false;
        // Como argumentos de línea de comandos: tienen prioridad sobre la
        // configuración de los tests. Con auto-index-creation el contexto conecta
        // al arrancar y, si no hay servidor, no se crea.
        assertThatThrownBy(() -> new SpringApplicationBuilder(PachocloSystemApplication.class)
                .web(WebApplicationType.SERVLET)
                .run("--server.port=0",
                        "--spring.mongodb.uri=mongodb://127.0.0.1:1/pachoclosystem_test?serverSelectionTimeoutMS=1000",
                        "--spring.mongodb.auto-index-creation=true")
                .close())
                .satisfies(error -> {
                    Throwable causa = NestedExceptionUtils.getMostSpecificCause(error);
                    assertThat(causa).isInstanceOf(MongoTimeoutException.class);
                    assertThat(causa.getMessage()).contains("Connection refused");
                });
    }
}
