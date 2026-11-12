package com.pachoclosystem.pachoclosystem.config;

import com.pachoclosystem.pachoclosystem.model.Rol;
import com.pachoclosystem.pachoclosystem.security.BaseDeDatosNoDisponibleFilter;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.web.cors.CorsConfigurationSource;

/**
 * Cadena de filtros de seguridad stateless con tokens JWT (HS256) y resource
 * server de OAuth2 (Nimbus).
 *
 * <p>Solo {@code POST /api/auth/login}, los health checks
 * ({@code GET /actuator/health/**}) y la documentación OpenAPI
 * ({@code /v3/api-docs}, {@code /swagger-ui/**}) son públicos; el resto exige un
 * token y cada ruta, un rol (tabla de permisos en el README y en
 * {@code AutorizacionPorRolTest}). Las rutas concretas van antes que las
 * generales: Spring aplica la primera regla que coincide. Cada recurso termina
 * en una regla solo-ADMIN, de modo que un endpoint nuevo sin regla propia queda
 * cerrado para los demás roles. Las reglas que dependen del cuerpo (el tipo de
 * registro del historial y el autor) están en {@code HistorialClinicoService}.
 *
 * <p>Un usuario con la contraseña pendiente de cambio solo tiene la autoridad
 * {@code CAMBIO_PASSWORD_PENDIENTE} (ver {@code JwtUsuarioAuthenticationConverter}):
 * puede leer {@code /api/auth/me} y cambiar su contraseña, y todo lo que exige
 * un rol le responde 403.</p>
 *
 * <p>Todo el estado de autenticación vive en el token <em>bearer</em>: sin
 * sesiones HTTP, sin login por formulario, sin Basic auth.</p>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private static final String ADMIN = Rol.ADMIN.name();
    private static final String DOCTOR = Rol.DOCTOR.name();
    private static final String ENFERMERO = Rol.ENFERMERO.name();
    private static final String[] TODOS = {ADMIN, DOCTOR, ENFERMERO};

    @Bean
    JwtEncoder jwtEncoder(ClaveFirmaJwt firma) {
        return NimbusJwtEncoder.withSecretKey(firma.clave())
                .algorithm(MacAlgorithm.HS256)
                .build();
    }

    @Bean
    JwtDecoder jwtDecoder(ClaveFirmaJwt firma) {
        NimbusJwtDecoder decodificador = NimbusJwtDecoder.withSecretKey(firma.clave())
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        // Valida firma HS256 (el decodificador restringido a HS256 rechaza "none"
        // y cualquier otro algoritmo), expiración/emitido-y-no-válido y el emisor
        // configurado (app.jwt.emisor / pachoclosystem).
        decodificador.setJwtValidator(JwtValidators.createDefaultWithIssuer(firma.emisor()));
        return decodificador;
    }

    @Bean
    SecurityFilterChain filtroSeguridad(HttpSecurity http,
                                        JwtDecoder decoder,
                                        Converter<Jwt, ? extends AbstractAuthenticationToken> conversor,
                                        AuthenticationEntryPoint puntoDeEntrada,
                                        AccessDeniedHandler accesoDenegado,
                                        @Qualifier("corsConfigurationSource") CorsConfigurationSource corsFuente)
            throws Exception {
        http
                // API stateless protegida con token bearer: no hay cookies ni sesiones,
                // así que no hay estado que un ataque CSRF pueda aprovechar; se desactiva.
                .csrf(csrf -> csrf.disable())
                // CORS por orígenes explícitos (CorsConfiguracion): las preflight OPTIONS
                // de un origen permitido se resuelven aquí, antes de la autenticación, y
                // no exigen token. Con app.cors.origenes vacío no se emite ninguna
                // cabecera Access-Control-Allow-*.
                .cors(cors -> cors.configurationSource(corsFuente))
                .sessionManagement(sesiones -> sesiones.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(login -> login.disable())
                .httpBasic(basica -> basica.disable())
                .logout(cierre -> cierre.disable())
                .authorizeHttpRequests(peticiones -> peticiones
                        .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                        // Health checks de la plataforma de despliegue (sin detalles internos).
                        .requestMatchers(HttpMethod.GET, "/actuator/health", "/actuator/health/**").permitAll()
                        // Documentación OpenAPI/Swagger: pública para poder consultarla sin token.
                        // Con SWAGGER_ENABLED=false (springdoc.api-docs.enabled) deja de existir.
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml",
                                "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        // Identidad y cambio de contraseña propia: cualquier usuario
                        // autenticado, también con la contraseña pendiente de cambio.
                        .requestMatchers(HttpMethod.GET, "/api/auth/me").authenticated()
                        .requestMatchers(HttpMethod.POST, "/api/auth/password").authenticated()
                        .requestMatchers("/api/usuarios/**").hasRole(ADMIN)
                        // Pacientes e historial clínico.
                        .requestMatchers(HttpMethod.GET, "/api/pacientes/**", "/api/historial")
                        .hasAnyRole(TODOS)
                        .requestMatchers(HttpMethod.PATCH, "/api/pacientes/*/habitacion").hasAnyRole(TODOS)
                        // Autor y tipo de registro los decide HistorialClinicoService (403 al ADMIN,
                        // que no tiene trabajador, y al enfermero en DIAGNOSTICO).
                        .requestMatchers(HttpMethod.POST, "/api/pacientes/*/historial").hasAnyRole(TODOS)
                        .requestMatchers(HttpMethod.POST, "/api/pacientes").hasAnyRole(ADMIN, DOCTOR)
                        .requestMatchers(HttpMethod.PUT, "/api/pacientes/*").hasAnyRole(ADMIN, DOCTOR)
                        // DELETE (baja lógica) y cualquier ruta sin regla propia.
                        .requestMatchers("/api/pacientes/**", "/api/historial/**").hasRole(ADMIN)
                        // Trabajadores.
                        .requestMatchers(HttpMethod.GET, "/api/trabajadores/**").hasAnyRole(ADMIN, DOCTOR)
                        .requestMatchers("/api/trabajadores/**").hasRole(ADMIN)
                        // Medicamentos. El descuento de stock de un registro de MEDICACION no pasa
                        // por aquí: lo hace el cliente interno contra MedicamentosService.
                        .requestMatchers(HttpMethod.GET, "/api/medicamentos/**").hasAnyRole(TODOS)
                        .requestMatchers(HttpMethod.POST, "/api/medicamentos/*/salidas")
                        .hasAnyRole(ADMIN, ENFERMERO)
                        .requestMatchers("/api/medicamentos/**").hasRole(ADMIN)
                        .anyRequest().authenticated())
                // 401 y 403 con el cuerpo de error uniforme de la API
                // (ErrorResponse, sin trazas ni detalles internos).
                .exceptionHandling(errores -> errores
                        .authenticationEntryPoint(puntoDeEntrada)
                        .accessDeniedHandler(accesoDenegado))
                .oauth2ResourceServer(recurso -> recurso
                        .authenticationEntryPoint(puntoDeEntrada)
                        .accessDeniedHandler(accesoDenegado)
                        .jwt(jwt -> jwt
                                .decoder(decoder)
                                .jwtAuthenticationConverter(conversor)))
                // 503 (nunca 401) si la base de datos falla al releer el usuario del token.
                .addFilterBefore(new BaseDeDatosNoDisponibleFilter(), BearerTokenAuthenticationFilter.class);
        return http.build();
    }
}