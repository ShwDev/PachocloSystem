package com.pachoclosystem.pachoclosystem.security;

import com.pachoclosystem.pachoclosystem.controller.MockMvcBaseTest;
import com.pachoclosystem.pachoclosystem.model.NivelExperiencia;
import com.pachoclosystem.pachoclosystem.model.Rol;
import com.pachoclosystem.pachoclosystem.model.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerExecutionChain;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.springframework.web.util.ServletRequestPathUtils;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static com.pachoclosystem.pachoclosystem.security.AutorizacionPorRolTest.Acceso.DENEGADO;
import static com.pachoclosystem.pachoclosystem.security.AutorizacionPorRolTest.Acceso.PERMITIDO;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

/**
 * Tabla de permisos por rol, la misma que la del README: cada fila es una
 * operación y lo que puede hacer cada rol. Es la única fuente de los tests de
 * autorización y se cruza con todos los estados de sesión:
 *
 * <ul>
 *   <li><strong>Sin token</strong> y con <strong>token mal firmado</strong>:
 *       401 exacto con el cuerpo uniforme en todas las filas. El único endpoint
 *       público es {@code POST /api/auth/login} ({@link #PUBLICOS}).</li>
 *   <li><strong>ADMIN, DOCTOR y ENFERMERO</strong>: PERMITIDO significa que la
 *       petición supera la autorización y puede acabar en 2xx, 400, 404 o 503
 *       (MedicamentosService no corre en los tests), pero nunca en 401 ni 403.
 *       DENEGADO es un 403 exacto con el cuerpo uniforme.</li>
 *   <li><strong>Contraseña pendiente de cambio</strong> (aunque sea ADMIN):
 *       solo las filas de {@link #CON_CAMBIO_PENDIENTE}; el resto, 403 con el
 *       aviso de cambiar la contraseña.</li>
 * </ul>
 *
 * <p>Las operaciones usan IDs inexistentes y cuerpos vacíos para no cambiar
 * datos. {@link #cadaEndpointMapeadoRecibeAlMenosUnaFila()} resuelve cada fila
 * con el {@code HandlerMapping} real y falla si un método de controlador no
 * recibe ninguna: un endpoint nuevo no puede quedar sin regla probada.</p>
 */
class AutorizacionPorRolTest extends MockMvcBaseTest {

    enum Acceso { PERMITIDO, DENEGADO }

    private static final String EVOLUCION = """
            {"tipo":"EVOLUCION","contenido":"Paciente estable y sin cambios"}""";
    private static final String DIAGNOSTICO = """
            {"tipo":"DIAGNOSTICO","contenido":"Hipertension leve"}""";
    private static final String MENSAJE_401 = "Debe autenticarse para acceder a este recurso.";
    private static final String MENSAJE_CAMBIO_PENDIENTE = "Debe cambiar su contraseña antes de continuar.";
    private static final AtomicInteger CONTADOR = new AtomicInteger();

    /** Operación, cuerpo y acceso esperado para ADMIN, DOCTOR y ENFERMERO. */
    private record Fila(HttpMethod metodo, String ruta, String cuerpo,
                        Acceso admin, Acceso doctor, Acceso enfermero) {

        String clave() {
            return metodo.name() + " " + ruta;
        }

        @Override
        public String toString() {
            return clave() + (cuerpo == null ? "" : " " + cuerpo.replaceAll("\\s+", ""));
        }
    }

    private static final List<Fila> TABLA = List.of(
            // Sesión
            new Fila(HttpMethod.GET, "/api/auth/me", null, PERMITIDO, PERMITIDO, PERMITIDO),
            new Fila(HttpMethod.POST, "/api/auth/password", "{}", PERMITIDO, PERMITIDO, PERMITIDO),
            // Pacientes
            new Fila(HttpMethod.GET, "/api/pacientes", null, PERMITIDO, PERMITIDO, PERMITIDO),
            new Fila(HttpMethod.GET, "/api/pacientes/PAC-9999", null, PERMITIDO, PERMITIDO, PERMITIDO),
            new Fila(HttpMethod.POST, "/api/pacientes", "{}", PERMITIDO, PERMITIDO, DENEGADO),
            new Fila(HttpMethod.PUT, "/api/pacientes/PAC-9999", "{}", PERMITIDO, PERMITIDO, DENEGADO),
            new Fila(HttpMethod.PATCH, "/api/pacientes/PAC-9999/habitacion", "{}", PERMITIDO, PERMITIDO, PERMITIDO),
            new Fila(HttpMethod.DELETE, "/api/pacientes/PAC-9999", null, PERMITIDO, DENEGADO, DENEGADO),
            // Historial clínico (el ADMIN no tiene trabajador para firmar; el enfermero no diagnostica)
            new Fila(HttpMethod.GET, "/api/historial", null, PERMITIDO, PERMITIDO, PERMITIDO),
            new Fila(HttpMethod.GET, "/api/pacientes/PAC-9999/historial", null, PERMITIDO, PERMITIDO, PERMITIDO),
            new Fila(HttpMethod.POST, "/api/pacientes/PAC-9999/historial", EVOLUCION, DENEGADO, PERMITIDO, PERMITIDO),
            new Fila(HttpMethod.POST, "/api/pacientes/PAC-9999/historial", DIAGNOSTICO, DENEGADO, PERMITIDO, DENEGADO),
            // Trabajadores
            new Fila(HttpMethod.GET, "/api/trabajadores", null, PERMITIDO, PERMITIDO, DENEGADO),
            new Fila(HttpMethod.GET, "/api/trabajadores/DOC-9999", null, PERMITIDO, PERMITIDO, DENEGADO),
            new Fila(HttpMethod.POST, "/api/trabajadores", "{}", PERMITIDO, DENEGADO, DENEGADO),
            new Fila(HttpMethod.PUT, "/api/trabajadores/DOC-9999", "{}", PERMITIDO, DENEGADO, DENEGADO),
            new Fila(HttpMethod.DELETE, "/api/trabajadores/DOC-9999", null, PERMITIDO, DENEGADO, DENEGADO),
            // Medicamentos e inventario
            new Fila(HttpMethod.GET, "/api/medicamentos", null, PERMITIDO, PERMITIDO, PERMITIDO),
            new Fila(HttpMethod.GET, "/api/medicamentos/MED-9999", null, PERMITIDO, PERMITIDO, PERMITIDO),
            new Fila(HttpMethod.GET, "/api/medicamentos/stock-bajo", null, PERMITIDO, PERMITIDO, PERMITIDO),
            new Fila(HttpMethod.GET, "/api/medicamentos/por-vencer", null, PERMITIDO, PERMITIDO, PERMITIDO),
            new Fila(HttpMethod.GET, "/api/medicamentos/vencidos", null, PERMITIDO, PERMITIDO, PERMITIDO),
            new Fila(HttpMethod.POST, "/api/medicamentos", "{}", PERMITIDO, DENEGADO, DENEGADO),
            new Fila(HttpMethod.PUT, "/api/medicamentos/MED-9999", "{}", PERMITIDO, DENEGADO, DENEGADO),
            new Fila(HttpMethod.DELETE, "/api/medicamentos/MED-9999", null, PERMITIDO, DENEGADO, DENEGADO),
            new Fila(HttpMethod.POST, "/api/medicamentos/MED-9999/entradas", "{}", PERMITIDO, DENEGADO, DENEGADO),
            new Fila(HttpMethod.POST, "/api/medicamentos/MED-9999/salidas", "{}", PERMITIDO, DENEGADO, PERMITIDO),
            // Usuarios
            new Fila(HttpMethod.GET, "/api/usuarios", null, PERMITIDO, DENEGADO, DENEGADO),
            new Fila(HttpMethod.GET, "/api/usuarios/USR-9999", null, PERMITIDO, DENEGADO, DENEGADO),
            new Fila(HttpMethod.POST, "/api/usuarios", "{}", PERMITIDO, DENEGADO, DENEGADO),
            new Fila(HttpMethod.PATCH, "/api/usuarios/USR-9999/rol", "{}", PERMITIDO, DENEGADO, DENEGADO),
            new Fila(HttpMethod.PATCH, "/api/usuarios/USR-9999/desactivar", null, PERMITIDO, DENEGADO, DENEGADO),
            new Fila(HttpMethod.PATCH, "/api/usuarios/USR-9999/activar", null, PERMITIDO, DENEGADO, DENEGADO),
            new Fila(HttpMethod.PATCH, "/api/usuarios/USR-9999/password", "{}", PERMITIDO, DENEGADO, DENEGADO));

    /** Los únicos endpoints sin token: no tienen fila porque no exigen rol. */
    private static final List<Fila> PUBLICOS = List.of(
            new Fila(HttpMethod.POST, "/api/auth/login", "{}", PERMITIDO, PERMITIDO, PERMITIDO));

    /** Lo único que puede hacer quien aún debe cambiar su contraseña. */
    private static final Set<String> CON_CAMBIO_PENDIENTE = Set.of("GET /api/auth/me", "POST /api/auth/password");

    /** Las rutas de los controladores (Actuator registra otro mapeo para sus endpoints). */
    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mapeoDeRutas;

    // ------------------------------------------------------------ por rol

    static Stream<Arguments> casos() {
        return TABLA.stream().flatMap(f -> Stream.of(
                Arguments.of(Rol.ADMIN, f, f.admin()),
                Arguments.of(Rol.DOCTOR, f, f.doctor()),
                Arguments.of(Rol.ENFERMERO, f, f.enfermero())));
    }

    @ParameterizedTest(name = "{0} {1} -> {2}")
    @MethodSource("casos")
    void tablaDePermisos(Rol rol, Fila fila, Acceso esperado) throws Exception {
        MvcResult resultado = mockMvc.perform(peticion(fila, tokenNuevoDe(rol))).andReturn();
        int estado = resultado.getResponse().getStatus();
        String cuerpo = cuerpo(resultado);

        if (esperado == PERMITIDO) {
            assertThat(estado).as("%s %s: %s", rol, fila, cuerpo).isNotIn(401, 403);
        } else {
            assertThat(estado).as("%s %s: %s", rol, fila, cuerpo).isEqualTo(403);
            comprobarCuerpoUniforme(cuerpo, 403, "Forbidden");
        }
    }

    // ------------------------------------------------- sin autenticación

    static Stream<Fila> filas() {
        return TABLA.stream();
    }

    @ParameterizedTest(name = "sin token {0} -> 401")
    @MethodSource("filas")
    void sinTokenTodasLasOperacionesResponden401Uniforme(Fila fila) throws Exception {
        comprobar401(mockMvc.perform(peticion(fila, null)).andReturn());
    }

    @ParameterizedTest(name = "token mal firmado {0} -> 401")
    @MethodSource("filas")
    void conUnTokenMalFirmadoTodasLasOperacionesResponden401(Fila fila) throws Exception {
        String token = tokenAdmin();
        String falsificado = token.substring(0, token.lastIndexOf('.') + 1) + "firma-falsificada";

        comprobar401(mockMvc.perform(peticion(fila, falsificado)).andReturn());
    }

    @Test
    void elLoginEsElUnicoEndpointPublico() throws Exception {
        MvcResult resultado = mockMvc.perform(request(HttpMethod.POST, "/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"admin\",\"password\":\"pwd-de-prueba-solo-tests-12345\"}"))
                .andReturn();

        assertThat(resultado.getResponse().getStatus()).isEqualTo(200);
        assertThat(cuerpo(resultado)).contains("\"token\":").contains("\"rol\":\"ADMIN\"");
        // Ninguna fila de la tabla es pública (lo comprueban los tests sin token)
        // y la lista de públicos es exactamente el login.
        assertThat(PUBLICOS).extracting(Fila::clave).containsExactly("POST /api/auth/login");
    }

    // ------------------------------------------- contraseña pendiente de cambio

    @ParameterizedTest(name = "cambio pendiente {0}")
    @MethodSource("filas")
    void conLaContrasenaPendienteSoloSePuedeVerseYCambiarla(Fila fila) throws Exception {
        MvcResult resultado = mockMvc.perform(peticion(fila, tokenAdminConCambioPendiente())).andReturn();
        int estado = resultado.getResponse().getStatus();
        String cuerpo = cuerpo(resultado);

        if (CON_CAMBIO_PENDIENTE.contains(fila.clave())) {
            assertThat(estado).as("%s: %s", fila, cuerpo).isNotIn(401, 403);
        } else {
            assertThat(estado).as("%s: %s", fila, cuerpo).isEqualTo(403);
            comprobarCuerpoUniforme(cuerpo, 403, "Forbidden");
            assertThat(cuerpo).contains(MENSAJE_CAMBIO_PENDIENTE);
        }
    }

    // ------------------------------------------------ rutas sin endpoint

    @ParameterizedTest(name = "ruta sin endpoint {0}")
    @ValueSource(strings = {"/", "/api", "/api/inexistente", "/api/pacientes/PAC-9999/otra",
            "/api/usuarios/USR-9999/inexistente", "/api/medicamentos/MED-9999/lotes", "/actuator/env"})
    void unaRutaSinEndpointNoQuedaAbierta(String ruta) throws Exception {
        comprobar401(mockMvc.perform(request(HttpMethod.GET, ruta)).andReturn());

        for (Rol rol : Rol.values()) {
            int estado = mockMvc.perform(request(HttpMethod.GET, ruta)
                            .header(HttpHeaders.AUTHORIZATION, "Bearer " + tokenNuevoDe(rol)))
                    .andReturn().getResponse().getStatus();
            assertThat(estado).as("%s GET %s", rol, ruta).isIn(403, 404);
        }
    }

    // ------------------------------------------------------- cobertura

    @Test
    void cadaEndpointMapeadoRecibeAlMenosUnaFila() throws Exception {
        Set<Method> atendidos = new HashSet<>();
        Set<String> filasSinEndpoint = new TreeSet<>();
        for (Fila fila : Stream.concat(TABLA.stream(), PUBLICOS.stream()).toList()) {
            Method metodo = metodoQueAtiende(fila);
            if (metodo == null) {
                filasSinEndpoint.add(fila.toString());
            } else {
                atendidos.add(metodo);
            }
        }

        Set<String> sinFila = new TreeSet<>();
        mapeoDeRutas.getHandlerMethods().forEach((info, manejador) -> {
            if (info.getPatternValues().contains("/error")) {
                return; // BasicErrorController de Spring Boot, no es API propia
            }
            // Swagger UI y /v3/api-docs los sirve springdoc: son públicos
            // (permitAll en SecurityConfig) y no forman parte de la tabla de permisos.
            if (manejador.getBeanType().getName().startsWith("org.springdoc.")) {
                return;
            }
            if (!atendidos.contains(manejador.getMethod())) {
                sinFila.add(info.getMethodsCondition().getMethods() + " " + info.getPatternValues());
            }
        });

        assertThat(filasSinEndpoint).as("filas que no llegan a ningún endpoint").isEmpty();
        assertThat(sinFila).as("endpoints sin fila en la tabla de permisos").isEmpty();
    }

    // ----------------------------------------------------------- apoyo

    /** Método de controlador que atiende la fila según el {@code HandlerMapping} real. */
    private Method metodoQueAtiende(Fila fila) throws Exception {
        MockHttpServletRequest peticion = new MockHttpServletRequest(fila.metodo().name(), fila.ruta());
        if (fila.cuerpo() != null) {
            peticion.setContentType(MediaType.APPLICATION_JSON_VALUE);
        }
        ServletRequestPathUtils.parseAndCache(peticion);
        HandlerExecutionChain cadena = mapeoDeRutas.getHandler(peticion);
        return cadena != null && cadena.getHandler() instanceof HandlerMethod manejador
                ? manejador.getMethod()
                : null;
    }

    private static MockHttpServletRequestBuilder peticion(Fila fila, String token) {
        MockHttpServletRequestBuilder peticion = request(fila.metodo(), fila.ruta());
        if (token != null) {
            peticion.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (fila.cuerpo() != null) {
            peticion.contentType(MediaType.APPLICATION_JSON).content(fila.cuerpo());
        }
        return peticion;
    }

    private static void comprobar401(MvcResult resultado) throws Exception {
        assertThat(resultado.getResponse().getStatus()).isEqualTo(401);
        assertThat(resultado.getResponse().getHeader(HttpHeaders.WWW_AUTHENTICATE)).isEqualTo("Bearer");
        String cuerpo = cuerpo(resultado);
        comprobarCuerpoUniforme(cuerpo, 401, "Unauthorized");
        assertThat(cuerpo).contains(MENSAJE_401);
    }

    private static void comprobarCuerpoUniforme(String cuerpo, int estado, String error) {
        assertThat(cuerpo)
                .contains("\"status\":" + estado)
                .contains("\"error\":\"" + error + "\"")
                .contains("\"mensajes\":[\"")
                .doesNotContain("Exception")
                .doesNotContain("com.pachoclosystem");
    }

    private static String cuerpo(MvcResult resultado) throws Exception {
        return resultado.getResponse().getContentAsString(StandardCharsets.UTF_8);
    }

    /** Token del admin inicial, o de un doctor/enfermero nuevo con su usuario. */
    private String tokenNuevoDe(Rol rol) throws Exception {
        return switch (rol) {
            case ADMIN -> tokenAdmin();
            case DOCTOR -> tokenDeTrabajador(registrarDoctor("Carlos Mena", "Cardiologia"));
            case ENFERMERO -> tokenDeTrabajador(registrarEnfermero("Lucia Vidal", NivelExperiencia.AVANZADO));
        };
    }

    /** Token de un ADMIN recién dado de alta con contraseña temporal. */
    private String tokenAdminConCambioPendiente() {
        Usuario usuario = usuarioService.crearUsuario("pendiente." + CONTADOR.incrementAndGet(),
                "clave-temporal-12345", Rol.ADMIN, null, true);
        return tokenService.generarToken(usuario);
    }
}
