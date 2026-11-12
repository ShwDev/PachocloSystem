package com.pachoclosystem.pachoclosystem.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import io.swagger.v3.oas.models.tags.Tag;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

/**
 * Documentación OpenAPI de PachocloSystem.
 *
 * <p>Se publica en {@code /v3/api-docs} (JSON) y {@code /swagger-ui.html}
 * (interfaz interactiva). Casi toda la API exige un token JWT, así que el
 * esquema <em>bearer</em> se declara aquí como requisito global; el login lo
 * desactiva con {@code @SecurityRequirements} porque es la ruta que emite el
 * token. {@code SecurityConfig} deja públicas solo estas rutas de documentación,
 * los health checks y el login.</p>
 *
 * <p>Se puede desactivar con {@code SWAGGER_ENABLED=false}.</p>
 */
@Configuration
public class OpenApiConfig {

    /** Nombre del esquema de seguridad (el que referencian los controladores). */
    public static final String ESQUEMA_BEARER = "bearerAuth";

    @Bean
    OpenAPI openApiPachocloSystem() {
        SecurityScheme bearer = new SecurityScheme()
                .type(SecurityScheme.Type.HTTP)
                .scheme("bearer")
                .bearerFormat("JWT")
                .description("""
                        Token JWT devuelto por `POST /api/auth/login`. Se envía como
                        `Authorization: Bearer <token>`. Caduca a los 30 minutos y deja de ser
                        válido en cuanto cambia la contraseña del usuario.
                        """);

        return new OpenAPI()
                .info(new Info()
                        .title("PachocloSystem API")
                        .version("1.0.0")
                        .summary("API principal del sistema hospitalario")
                        .description("""
                                Puerta de entrada única del sistema: autenticación JWT, permisos por rol,
                                pacientes, trabajadores, usuarios, historial clínico e inventario de
                                medicamentos (este último delegado en MedicamentosService).

                                ## Autenticación
                                1. `POST /api/auth/login` con username y contraseña.
                                2. Enviar el `token` de la respuesta como `Authorization: Bearer <token>`
                                   en todas las demás peticiones.

                                El token caduca a los 30 minutos. Si el usuario tiene la contraseña
                                pendiente de cambio, solo puede consultar `/api/auth/me` y cambiarla en
                                `/api/auth/password`; el resto de rutas responde 403.

                                ## Permisos
                                Cada operación exige un rol: **ADMIN** (gestión completa), **DOCTOR**
                                (pacientes, historial y lectura de trabajadores y medicamentos) o
                                **ENFERMERO** (habitación, historial y salidas de stock). La tabla
                                completa está en el README del backend.

                                ## Idempotencia
                                `POST /api/pacientes/{id}/historial` acepta la cabecera `Idempotency-Key`
                                (16 a 100 caracteres: letras, dígitos, guion o guion bajo). Repetir la
                                petición con la misma clave devuelve la misma respuesta con
                                `Idempotency-Replayed: true`, sin crear otro registro ni descontar stock
                                dos veces. La ruta de salidas de medicamentos de este servicio no
                                reenvía esa cabecera al microservicio.

                                ## Errores
                                Todos los errores usan el mismo cuerpo `ErrorResponse`
                                (`status`, `error` y `mensajes`), sin trazas ni detalles internos.
                                Un 503 indica que la base de datos o MedicamentosService no están
                                disponibles; repetir la petición es seguro.
                                """)
                        .license(new License().name("Uso académico")))
                // Relativo al host actual: sirve igual en local (8080) y en cualquier despliegue.
                .servers(List.of(new Server().url("/").description("Servidor actual")))
                .components(new Components().addSecuritySchemes(ESQUEMA_BEARER, bearer))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_BEARER))
                .tags(List.of(
                        new Tag().name("Autenticación")
                                .description("Login, usuario actual y cambio de contraseña propia."),
                        new Tag().name("Pacientes")
                                .description("Alta, consulta, edición, cambio de habitación y baja lógica."),
                        new Tag().name("Trabajadores")
                                .description("Doctores y enfermeros del hospital."),
                        new Tag().name("Usuarios")
                                .description("Usuarios de acceso, solo para ADMIN. Nunca exponen contraseñas."),
                        new Tag().name("Historial clínico")
                                .description("Registros firmados por el trabajador del usuario autenticado."),
                        new Tag().name("Medicamentos")
                                .description("Inventario a través de MedicamentosService, con el token del usuario.")));
    }
}
