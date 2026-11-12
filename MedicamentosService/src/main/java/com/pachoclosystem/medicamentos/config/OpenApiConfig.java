package com.pachoclosystem.medicamentos.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Documentación OpenAPI de MedicamentosService.
 *
 * <p>Se publica en {@code /v3/api-docs} (JSON) y {@code /swagger-ui.html}
 * (interfaz interactiva). Todo {@code /api/**} exige la cabecera
 * {@code X-Api-Key}, así que el esquema de seguridad se declara una vez aquí y
 * se aplica a todas las operaciones. Las rutas de documentación y los health
 * checks quedan fuera de {@code /api/**} y no necesitan la clave.</p>
 *
 * <p>Se puede desactivar con {@code SWAGGER_ENABLED=false}.</p>
 */
@Configuration
public class OpenApiConfig {

    /** Nombre del esquema de seguridad (el que referencian los controladores). */
    public static final String ESQUEMA_CLAVE_SERVICIO = "claveServicio";

    @Bean
    OpenAPI openApiMedicamentos() {
        SecurityScheme claveServicio = new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name(ClaveServicioFilter.CABECERA)
                .description("""
                        Clave compartida entre PachocloSystem y MedicamentosService
                        (`MEDICAMENTOS_API_KEY`). Va en la cabecera `X-Api-Key`.
                        Si la clave está vacía (solo desarrollo), el servicio no la exige.
                        """);

        return new OpenAPI()
                .info(new Info()
                        .title("PachocloSystem · MedicamentosService")
                        .version("1.0.0")
                        .summary("Inventario de medicamentos: stock, entradas, salidas y vencimientos")
                        .description("""
                                Microservicio interno del inventario hospitalario, con persistencia en MongoDB.

                                **Solo lo llama PachocloSystem**, que le reenvía las peticiones ya autenticadas
                                del usuario. El navegador nunca habla directamente con este servicio.

                                ## Autenticación
                                Todas las rutas `/api/**` exigen la cabecera `X-Api-Key` con la clave
                                compartida entre los dos servicios. Las rutas de documentación y los health
                                checks no la necesitan.

                                ## Salidas de stock idempotentes
                                `POST /api/medicamentos/{id}/salidas` acepta la cabecera `Idempotency-Key`
                                (16 a 100 caracteres: letras, dígitos, guion o guion bajo). Repetir la
                                petición con la misma clave y los mismos datos devuelve la misma respuesta
                                con `Idempotency-Replayed: true`, sin volver a descontar stock.

                                ## Errores
                                Todos los errores usan el mismo cuerpo `ErrorResponse`
                                (`status`, `error` y `mensajes`), sin trazas ni detalles internos.
                                """)
                        .license(new io.swagger.v3.oas.models.info.License()
                                .name("Uso académico")))
                // Relativo al host actual: sirve igual en local (8081) y en cualquier despliegue.
                .servers(List.of(new Server().url("/").description("Servidor actual")))
                .components(new Components().addSecuritySchemes(ESQUEMA_CLAVE_SERVICIO, claveServicio))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_CLAVE_SERVICIO))
                .tags(List.of(new Tag()
                        .name("Medicamentos")
                        .description("""
                                Alta, consulta, edición y baja del inventario, entradas y salidas de
                                stock, y listados de stock bajo, próximos a vencer y vencidos.
                                """)));
    }
}
