package com.pachoclosystem.pachoclosystem.config;

import com.pachoclosystem.pachoclosystem.PachocloSystemApplication;
import org.junit.jupiter.api.Test;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.core.NestedExceptionUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Arranque real con el perfil prod sin la configuración de producción: el
 * servicio no arranca, explica qué falta y no muestra ningún valor. (Sin el
 * perfil, el resto de tests arranca la aplicación como siempre.)
 */
class PerfilProduccionArranqueTest {

    @Test
    void conElPerfilProdYSinSecretosNoArranca() {
        String uriMongo = System.getenv("SPRING_MONGODB_URI");
        assertThatThrownBy(() -> new SpringApplicationBuilder(PachocloSystemApplication.class)
                .run("--spring.profiles.active=prod", "--server.port=0")
                .close())
                .satisfies(error -> {
                    Throwable causa = NestedExceptionUtils.getMostSpecificCause(error);
                    assertThat(causa).isInstanceOf(IllegalStateException.class);
                    assertThat(causa.getMessage())
                            .contains("Configuración de producción incompleta (perfil prod)")
                            .contains("MEDICAMENTOS_API_KEY", "MEDICAMENTOS_URL", "SPRING_MONGODB_URI", "CORS_ORIGENES")
                            .doesNotContain("clave-jwt-de-prueba-para-tests-1234567890abcd", "pwd-de-prueba-solo-tests-12345");
                    if (uriMongo != null && !uriMongo.isBlank()) {
                        assertThat(causa.getMessage()).doesNotContain(uriMongo);
                    }
                });
    }
}
