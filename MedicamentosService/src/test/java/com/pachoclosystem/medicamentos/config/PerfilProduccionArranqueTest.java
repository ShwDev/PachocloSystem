package com.pachoclosystem.medicamentos.config;

import com.pachoclosystem.medicamentos.MedicamentosApplication;
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
        assertThatThrownBy(() -> new SpringApplicationBuilder(MedicamentosApplication.class)
                .run("--spring.profiles.active=prod", "--server.port=0")
                .close())
                .satisfies(error -> {
                    Throwable causa = NestedExceptionUtils.getMostSpecificCause(error);
                    assertThat(causa).isInstanceOf(IllegalStateException.class);
                    assertThat(causa.getMessage())
                            .contains("Configuración de producción incompleta (perfil prod)")
                            .contains("MEDICAMENTOS_API_KEY", "SPRING_MONGODB_URI")
                            .doesNotContain("mongodb://", "mongodb+srv://");
                    if (uriMongo != null && !uriMongo.isBlank()) {
                        assertThat(causa.getMessage()).doesNotContain(uriMongo);
                    }
                });
    }
}
