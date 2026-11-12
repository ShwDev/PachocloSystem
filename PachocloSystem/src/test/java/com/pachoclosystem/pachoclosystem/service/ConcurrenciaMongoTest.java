package com.pachoclosystem.pachoclosystem.service;

import com.pachoclosystem.pachoclosystem.controller.MockMvcBaseTest;
import com.pachoclosystem.pachoclosystem.dto.LoginResponse;
import com.pachoclosystem.pachoclosystem.exception.ConflictoException;
import com.pachoclosystem.pachoclosystem.exception.CredencialesInvalidasException;
import com.pachoclosystem.pachoclosystem.exception.NotFoundException;
import com.pachoclosystem.pachoclosystem.model.Paciente;
import com.pachoclosystem.pachoclosystem.model.Rol;
import com.pachoclosystem.pachoclosystem.model.Usuario;
import com.pachoclosystem.pachoclosystem.security.AuthService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpHeaders;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Concurrencia contra MongoDB real: las garantías que antes daban los cerrojos
 * de la JVM ahora las dan los índices únicos, las escrituras atómicas con
 * guardas en la consulta y el cerrojo administrativo transaccional.
 */
class ConcurrenciaMongoTest extends MockMvcBaseTest {

    private static final String PASSWORD_A = "password-segura-aaaa1";
    private static final String PASSWORD_B = "password-segura-bbbb2";

    @Autowired
    private PacienteService pacienteService;

    @Autowired
    private TrabajadorService trabajadorService;

    @Autowired
    private AuthService authService;

    private final ExecutorService ejecutor = Executors.newFixedThreadPool(8);

    @AfterEach
    void cerrarEjecutor() {
        ejecutor.shutdownNow();
    }

    @Test
    void altasSimultaneasConElMismoUsernameDejanUnSoloUsuario() throws Exception {
        List<Boolean> resultados = aLaVez(8, () -> {
            try {
                usuarioService.crearUsuario("mismo.nombre", PASSWORD_A, Rol.ADMIN, null);
                return true;
            } catch (ConflictoException duplicado) {
                return false;
            }
        });

        assertThat(resultados).containsOnlyOnce(true);
        assertThat(repositorioUsuarios.listarTodos()).filteredOn(u -> u.getUsername().equals("mismo.nombre"))
                .hasSize(1);
    }

    @Test
    void dosUsuariosQueSeVinculanALaVezAlMismoTrabajadorSoloUnoLoConsigue() throws Exception {
        String idDoctor = trabajadorService.registrarTrabajador("Carlos Mena", "Doctor", "Cardiologia", null)
                .getIdTrabajador();
        int[] contador = {0};

        List<Boolean> resultados = aLaVez(6, () -> {
            String username;
            synchronized (contador) {
                username = "doctor." + (++contador[0]);
            }
            try {
                usuarioService.crearUsuario(username, PASSWORD_A, Rol.DOCTOR, idDoctor);
                return true;
            } catch (ConflictoException ocupado) {
                return false;
            }
        });

        assertThat(resultados).containsOnlyOnce(true);
        assertThat(repositorioUsuarios.listarTodos()).filteredOn(u -> idDoctor.equals(u.getIdTrabajador()))
                .hasSize(1);
    }

    @Test
    void dosAdministradoresQueSeDesactivanALaVezNoDejanElSistemaSinAdministradores() throws Exception {
        // Sin el admin de los tests, uno y dos son los únicos administradores activos.
        mongoTemplate.updateMulti(new Query(Criteria.where("rol").is(Rol.ADMIN)),
                new Update().set("activo", false), Usuario.class);
        for (int ronda = 0; ronda < 20; ronda++) {
            Usuario uno = usuarioService.crearUsuario("admin.uno." + ronda, PASSWORD_A, Rol.ADMIN, null);
            Usuario dos = usuarioService.crearUsuario("admin.dos." + ronda, PASSWORD_A, Rol.ADMIN, null);
            CountDownLatch salida = new CountDownLatch(1);
            Future<?> a = ejecutor.submit(() -> desactivarIgnorandoConflicto(salida, dos.getIdUsuario(),
                    uno.getUsername()));
            Future<?> b = ejecutor.submit(() -> desactivarIgnorandoConflicto(salida, uno.getIdUsuario(),
                    dos.getUsername()));
            salida.countDown();
            a.get(10, TimeUnit.SECONDS);
            b.get(10, TimeUnit.SECONDS);

            assertThat(repositorioUsuarios.contarAdministradoresActivos()).isEqualTo(1);
            mongoTemplate.updateMulti(new Query(Criteria.where("_id").in(uno.getIdUsuario(), dos.getIdUsuario())),
                    new Update().set("activo", false), Usuario.class);
        }
    }

    @Test
    void dosBajasSimultaneasDelMismoPacienteSoloUnaTieneExito() throws Exception {
        for (int ronda = 0; ronda < 10; ronda++) {
            Paciente paciente = pacienteService.registrarPaciente("Ana Torres", 30, 101);

            List<Boolean> resultados = aLaVez(2, () -> {
                try {
                    pacienteService.eliminarPaciente(paciente.getIdPaciente());
                    return true;
                } catch (NotFoundException yaDeBaja) {
                    return false;
                }
            });

            assertThat(resultados).containsExactlyInAnyOrder(true, false);
            assertThat(repositorioPacientes.buscarPorId(paciente.getIdPaciente()).isActivo()).isFalse();
        }
    }

    /**
     * Un login con la contraseña anterior que coincide con el cambio nunca deja
     * un token válido: o falla, o su token lleva la versión anterior y se rechaza.
     */
    @Test
    void unLoginSimultaneoAlCambioDePasswordNoDejaUnTokenValidoConLaAnterior() throws Exception {
        Usuario usuario = usuarioService.crearUsuario("ana.torres", PASSWORD_A, Rol.ADMIN, null);
        String anterior = PASSWORD_A;
        String nueva = PASSWORD_B;
        for (int ronda = 0; ronda < 15; ronda++) {
            limitadorIntentos.reiniciar();
            String conAnterior = anterior;
            String cambioA = nueva;
            CountDownLatch salida = new CountDownLatch(1);
            Future<LoginResponse> login = ejecutor.submit(() -> {
                salida.await();
                try {
                    return authService.iniciarSesion("ana.torres", conAnterior, "10.0.0.1");
                } catch (CredencialesInvalidasException rechazado) {
                    return null;
                }
            });
            Future<?> cambio = ejecutor.submit(() -> {
                salida.await();
                return usuarioService.restablecerPassword(usuario.getIdUsuario(), cambioA);
            });
            salida.countDown();
            LoginResponse respuesta = login.get(10, TimeUnit.SECONDS);
            cambio.get(10, TimeUnit.SECONDS);

            if (respuesta != null) {
                mockMvc.perform(get("/api/auth/me")
                                .header(HttpHeaders.AUTHORIZATION, "Bearer " + respuesta.token()))
                        .andExpect(status().isUnauthorized());
            }
            anterior = nueva;
            nueva = conAnterior;
        }
        // Con la contraseña vigente sí se entra y el token vale.
        limitadorIntentos.reiniciar();
        LoginResponse vigente = authService.iniciarSesion("ana.torres", anterior, "10.0.0.1");
        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + vigente.token()))
                .andExpect(status().isOk());
    }

    private void desactivarIgnorandoConflicto(CountDownLatch salida, String id, String solicitante) {
        try {
            salida.await();
            usuarioService.desactivar(id, solicitante);
        } catch (ConflictoException esperado) {
            // Uno de los dos debe perder: es lo que se comprueba.
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Lanza {@code hilos} ejecuciones de {@code accion} a la vez y devuelve sus resultados. */
    private <T> List<T> aLaVez(int hilos, Callable<T> accion) throws Exception {
        CountDownLatch salida = new CountDownLatch(1);
        List<Future<T>> futuros = new ArrayList<>();
        for (int i = 0; i < hilos; i++) {
            futuros.add(ejecutor.submit(() -> {
                salida.await();
                return accion.call();
            }));
        }
        salida.countDown();
        List<T> resultados = new ArrayList<>();
        for (Future<T> futuro : futuros) {
            resultados.add(futuro.get(15, TimeUnit.SECONDS));
        }
        return resultados;
    }
}
