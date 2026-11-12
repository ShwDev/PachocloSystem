package com.pachoclosystem.medicamentos;

import com.mongodb.MongoTimeoutException;
import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.core.NestedExceptionUtils;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * MongoDB cae con el servicio ya en marcha: cada petición responde 503 con el
 * cuerpo de error uniforme, sin detalles internos. Con la configuración real,
 * además, el servicio no arrancaría (segundo test).
 *
 * <p>La caída se simula envolviendo el {@link MongoClient} con un proxy: mientras
 * {@link #CAIDA} está activa, cualquier operación contra la base falla como
 * cuando el driver no encuentra el servidor. Se envuelve el cliente y no la
 * fábrica de base de datos porque el chequeo de salud de Actuator usa el
 * {@code MongoClient} directamente: así también pasa a DOWN.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
class BaseDatosNoDisponibleTest {

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

    @AfterEach
    void levantarLaBase() {
        CAIDA = false;
    }

    @Test
    void unaConsultaSinBaseDeDatosResponde503Uniforme() throws Exception {
        CAIDA = true;
        mockMvc.perform(get("/api/medicamentos"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.error").value("Service Unavailable"))
                .andExpect(jsonPath("$.mensajes[0]").value(MENSAJE_503))
                .andExpect(content().string(not(containsString("Exception"))))
                .andExpect(content().string(not(containsString("mongodb"))));
    }

    @Test
    void unaSalidaSinBaseDeDatosResponde503() throws Exception {
        CAIDA = true;
        mockMvc.perform(post("/api/medicamentos/MED-0001/salidas")
                        .header("Idempotency-Key", "8f14e45f-ceea-4672-a5b1-7a0c3c9e2f01")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cantidad\":1}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensajes[0]").value(MENSAJE_503));
    }

    @Test
    void elHealthCheckRespondeDownPeroElProcesoSigueVivo() throws Exception {
        CAIDA = true;
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value("DOWN"));
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isServiceUnavailable());
        // El proceso sigue vivo: no hay que reiniciarlo por una caída de la base.
        mockMvc.perform(get("/actuator/health/liveness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    void alVolverLaBaseLaMismaPeticionFunciona() throws Exception {
        CAIDA = true;
        mockMvc.perform(get("/api/medicamentos"))
                .andExpect(status().isServiceUnavailable());
        CAIDA = false;
        mockMvc.perform(get("/api/medicamentos")).andExpect(status().isOk());
    }

    @Test
    void sinBaseDeDatosElServicioNoArranca() {
        CAIDA = false;
        // Como argumentos de línea de comandos: tienen prioridad sobre la
        // configuración de los tests. Con auto-index-creation el contexto conecta
        // al arrancar y, si no hay servidor, no se crea.
        assertThatThrownBy(() -> new SpringApplicationBuilder(MedicamentosApplication.class)
                .web(WebApplicationType.NONE)
                .run("--spring.mongodb.uri=mongodb://127.0.0.1:1/medicamentos_test?serverSelectionTimeoutMS=1000",
                        "--spring.mongodb.auto-index-creation=true")
                .close())
                .satisfies(error -> {
                    Throwable causa = NestedExceptionUtils.getMostSpecificCause(error);
                    assertThat(causa).isInstanceOf(MongoTimeoutException.class);
                    assertThat(causa.getMessage()).contains("Connection refused");
                });
    }
}
