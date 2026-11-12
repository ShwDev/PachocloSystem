package com.pachoclosystem.pachoclosystem.service;

import com.pachoclosystem.pachoclosystem.exception.ConflictoException;
import com.pachoclosystem.pachoclosystem.exception.NotFoundException;
import com.pachoclosystem.pachoclosystem.exception.SolicitudInvalidaException;
import com.pachoclosystem.pachoclosystem.model.Doctor;
import com.pachoclosystem.pachoclosystem.model.Enfermero;
import com.pachoclosystem.pachoclosystem.model.NivelExperiencia;
import com.pachoclosystem.pachoclosystem.model.Rol;
import com.pachoclosystem.pachoclosystem.model.Usuario;
import com.pachoclosystem.pachoclosystem.repository.TrabajadorRepositoryEnMemoria;
import com.pachoclosystem.pachoclosystem.repository.UsuarioRepositoryEnMemoria;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;

/** Reglas de negocio de los usuarios, sin contexto Spring. */
class UsuarioServiceTest {

    private static final String PASSWORD_VALIDA = "clave-de-pruebas-10";

    private UsuarioRepositoryEnMemoria repositorio;
    private TrabajadorService trabajadorService;
    private PasswordEncoder encoder;
    private UsuarioService servicio;

    @BeforeEach
    void preparar() {
        repositorio = new UsuarioRepositoryEnMemoria();
        trabajadorService = new TrabajadorService(new TrabajadorRepositoryEnMemoria(),
                new UsuarioRepositoryEnMemoria());
        encoder = new BCryptPasswordEncoder();
        servicio = new UsuarioService(repositorio, trabajadorService, encoder);
    }

    // ---------------------------------------------------------------- camino feliz

    @Test
    void crearAdminGeneraIdUsrYGuardaSoloElHash() {
        Usuario admin = servicio.crearUsuario("Ana.Torres", PASSWORD_VALIDA, Rol.ADMIN, null);

        assertThat(admin.getIdUsuario()).isEqualTo("USR-0001");
        assertThat(admin.getUsername()).isEqualTo("ana.torres");
        assertThat(admin.getRol()).isEqualTo(Rol.ADMIN);
        assertThat(admin.getIdTrabajador()).isNull();
        assertThat(admin.isActivo()).isTrue();

        // La contraseña en claro nunca se guarda: solo el hash BCrypt.
        assertThat(admin.getPasswordHash()).startsWith("$2").isNotEqualTo(PASSWORD_VALIDA);
        assertThat(encoder.matches(PASSWORD_VALIDA, admin.getPasswordHash())).isTrue();
        assertThat(encoder.matches("otra-clave-distinta", admin.getPasswordHash())).isFalse();
    }

    @Test
    void crearAdminNormalizaElUsernameAMinusculas() {
        Usuario admin = servicio.crearUsuario("  ADMIN.Local  ", PASSWORD_VALIDA, Rol.ADMIN, null);

        assertThat(admin.getUsername()).isEqualTo("admin.local");
        assertThat(servicio.buscarPorUsername("ADMIN.LOCAL").getIdUsuario()).isEqualTo("USR-0001");
        assertThat(repositorio.existePorUsername("Admin.Local")).isTrue();
    }

    @Test
    void crearDoctorVinculadoAlTrabajadorCorrespondiente() {
        Doctor doctor = (Doctor) trabajadorService.registrarTrabajador(
                "Carlos Mena", "Doctor", "Cardiologia", null);

        Usuario usuario = servicio.crearUsuario("carlos.mena", PASSWORD_VALIDA, Rol.DOCTOR,
                doctor.getIdTrabajador());

        assertThat(usuario.getIdTrabajador()).isEqualTo("DOC-0001");
        assertThat(repositorio.buscarPorIdTrabajador("DOC-0001")).isSameAs(usuario);
        assertThat(repositorio.listarTodos()).containsExactly(usuario);
    }

    @Test
    void crearEnfermeroVinculadoAlTrabajadorCorrespondiente() {
        Enfermero enfermero = (Enfermero) trabajadorService.registrarTrabajador(
                "Lucia Vidal", "Enfermero", null, NivelExperiencia.NOVATO);

        Usuario usuario = servicio.crearUsuario("lucia.vidal", PASSWORD_VALIDA, Rol.ENFERMERO,
                enfermero.getIdTrabajador());

        assertThat(usuario.getRol()).isEqualTo(Rol.ENFERMERO);
        assertThat(usuario.getIdTrabajador()).isEqualTo("ENF-0001");
        assertThat(repositorio.buscarPorIdTrabajador("ENF-0001")).isSameAs(usuario);
    }

    @Test
    void sePermitenVariosAdministradoresSinVincular() {
        servicio.crearUsuario("admin.primero", PASSWORD_VALIDA, Rol.ADMIN, null);
        servicio.crearUsuario("admin.segundo", PASSWORD_VALIDA, Rol.ADMIN, null);

        assertThat(repositorio.listarTodos()).hasSize(2);
    }

    // ------------------------------------------------------------------ regla a)

    @Test
    void usernameInvalidoRechazaFormatoNoPermitido() {
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.crearUsuario("ab", PASSWORD_VALIDA, Rol.ADMIN, null))
                .withMessageContaining("El username debe tener entre 3 y 30 caracteres");

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.crearUsuario("con espacios", PASSWORD_VALIDA, Rol.ADMIN, null))
                .withMessageContaining("El username debe tener entre 3 y 30 caracteres");

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.crearUsuario("username.demasiado.largo.para.el.formato",
                        PASSWORD_VALIDA, Rol.ADMIN, null))
                .withMessageContaining("El username debe tener entre 3 y 30 caracteres");
    }

    @Test
    void usernameRepetidoRechazaConMayusculasDistintas() {
        servicio.crearUsuario("ana.torres", PASSWORD_VALIDA, Rol.ADMIN, null);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.crearUsuario("ANA.TORRES", PASSWORD_VALIDA, Rol.ADMIN, null))
                .withMessage("Ya existe un usuario con el username ana.torres.");

        assertThat(repositorio.listarTodos()).hasSize(1);
    }

    @Test
    void altasSimultaneasConElMismoUsernameDejanUnSoloUsuarioYElRestoRecibeConflicto()
            throws Exception {
        int hilos = 8;
        ExecutorService ejecutor = Executors.newFixedThreadPool(hilos);
        CountDownLatch salida = new CountDownLatch(1);
        try {
            List<Future<Boolean>> intentos = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                intentos.add(ejecutor.submit(() -> {
                    salida.await();
                    try {
                        servicio.crearUsuario("mismo.nombre", PASSWORD_VALIDA, Rol.ADMIN, null);
                        return true;
                    } catch (ConflictoException duplicado) {
                        return false;
                    }
                }));
            }
            salida.countDown();

            int creados = 0;
            for (Future<Boolean> intento : intentos) {
                creados += intento.get(10, TimeUnit.SECONDS) ? 1 : 0;
            }
            assertThat(creados).isEqualTo(1);
            assertThat(repositorio.listarTodos()).hasSize(1);
        } finally {
            ejecutor.shutdownNow();
        }
    }

    @Test
    void usernameRepetidoNoConsumeIdDeUsuario() {
        servicio.crearUsuario("ana.torres", PASSWORD_VALIDA, Rol.ADMIN, null);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.crearUsuario("ana.torres", PASSWORD_VALIDA, Rol.ADMIN, null));

        assertThat(servicio.crearUsuario("otro.usuario", PASSWORD_VALIDA, Rol.ADMIN, null).getIdUsuario())
                .isEqualTo("USR-0002");
    }

    // ------------------------------------------------------------------ regla b)

    @Test
    void passwordCortaRechazaSinRevelarLaPassword() {
        String passwordCorta = "secreto1";

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.crearUsuario("ana.torres", passwordCorta, Rol.ADMIN, null))
                .withMessage("La contraseña debe tener al menos 10 caracteres.")
                // El mensaje nunca contiene la contraseña intentada.
                .satisfies(excepcion -> assertThat(excepcion.getMessage())
                        .doesNotContain(passwordCorta)
                        .doesNotContain("secreto"));
    }

    @Test
    void passwordNulaSeTrataComoInvalida() {
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.crearUsuario("ana.torres", null, Rol.ADMIN, null))
                .withMessage("La contraseña debe tener al menos 10 caracteres.");
    }

    @Test
    void passwordDeMasDe72BytesRechazaContandoBytesNoCaracteres() {
        // 37 eñes son 37 caracteres pero 74 bytes en UTF-8.
        String larga = "ñ".repeat(37);

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.crearUsuario("ana.torres", larga, Rol.ADMIN, null))
                .withMessage("La contraseña no puede ocupar más de 72 bytes "
                        + "(las letras con tilde y la ñ ocupan 2).")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain(larga));
        assertThat(repositorio.listarTodos()).isEmpty();
    }

    @Test
    void passwordDeExactamente72BytesEsValida() {
        String limite = "ñ".repeat(36);

        Usuario usuario = servicio.crearUsuario("ana.torres", limite, Rol.ADMIN, null);

        assertThat(encoder.matches(limite, usuario.getPasswordHash())).isTrue();
    }

    @Test
    void passwordIgualAlUsernameRechazaSinDistinguirMayusculas() {
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.crearUsuario("ana.torres.g", "Ana.Torres.G", Rol.ADMIN, null))
                .withMessage("La contraseña no puede ser igual al username.");
    }

    @Test
    void passwordExactamenteDiezCaracteresEsValida() {
        Usuario usuario = servicio.crearUsuario("ana.torres", "1234567890", Rol.ADMIN, null);

        assertThat(encoder.matches("1234567890", usuario.getPasswordHash())).isTrue();
    }

    // ------------------------------------------------------------------ regla c)

    @Test
    void adminNoPuedeEstarVinculadoATrabajador() {
        Doctor doctor = (Doctor) trabajadorService.registrarTrabajador(
                "Carlos Mena", "Doctor", "Cardiologia", null);

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.crearUsuario("ana.torres", PASSWORD_VALIDA, Rol.ADMIN,
                        doctor.getIdTrabajador()))
                .withMessage("El usuario administrador no puede estar vinculado a un trabajador.");
    }

    // ------------------------------------------------------------------ regla d)

    @Test
    void doctorRequiereTrabajadorVinculado() {
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.crearUsuario("carlos.mena", PASSWORD_VALIDA, Rol.DOCTOR, null))
                .withMessage("El usuario con rol DOCTOR debe estar vinculado a un trabajador.");
    }

    @Test
    void doctorConTrabajadorInexistenteLanzaNotFound() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.crearUsuario("carlos.mena", PASSWORD_VALIDA, Rol.DOCTOR,
                        "DOC-9999"))
                .withMessage("No se encontró el trabajador DOC-9999.");
    }

    @Test
    void doctorConTrabajadorQueNoEsDoctorRechaza() {
        Enfermero enfermero = (Enfermero) trabajadorService.registrarTrabajador(
                "Lucia Vidal", "Enfermero", null, NivelExperiencia.NOVATO);

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.crearUsuario("carlos.mena", PASSWORD_VALIDA, Rol.DOCTOR,
                        enfermero.getIdTrabajador()))
                .withMessage("El trabajador ENF-0001 no es un Doctor.");
    }

    // ------------------------------------------------------------------ regla e)

    @Test
    void enfermeroRequiereTrabajadorVinculado() {
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.crearUsuario("lucia.vidal", PASSWORD_VALIDA, Rol.ENFERMERO, null))
                .withMessage("El usuario con rol ENFERMERO debe estar vinculado a un trabajador.");
    }

    @Test
    void enfermeroConTrabajadorQueNoEsEnfermeroRechaza() {
        Doctor doctor = (Doctor) trabajadorService.registrarTrabajador(
                "Carlos Mena", "Doctor", "Cardiologia", null);

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.crearUsuario("lucia.vidal", PASSWORD_VALIDA, Rol.ENFERMERO,
                        doctor.getIdTrabajador()))
                .withMessage("El trabajador DOC-0001 no es un Enfermero.");
    }

    // ------------------------------------------------------------------ regla f)

    @Test
    void unTrabajadorNoPuedeTenerDosUsuarios() {
        Doctor doctor = (Doctor) trabajadorService.registrarTrabajador(
                "Carlos Mena", "Doctor", "Cardiologia", null);
        servicio.crearUsuario("carlos.mena", PASSWORD_VALIDA, Rol.DOCTOR, doctor.getIdTrabajador());

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.crearUsuario("otro.nombre", PASSWORD_VALIDA, Rol.DOCTOR,
                        doctor.getIdTrabajador()))
                .withMessage("El trabajador DOC-0001 ya tiene un usuario.");

        assertThat(repositorio.listarTodos()).hasSize(1);
    }

    // ------------------------------------------------------------------ rol nulo)

    @Test
    void rolNuloRechaza() {
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.crearUsuario("ana.torres", PASSWORD_VALIDA, null, null))
                .withMessage("Debe seleccionar un rol (ADMIN, DOCTOR o ENFERMERO).");
    }

    // ------------------------------------------------------------- buscarPorUsername

    @Test
    void buscarPorUsernameDevuelveElUsuarioSinImportarMayusculas() {
        servicio.crearUsuario("ana.torres", PASSWORD_VALIDA, Rol.ADMIN, null);

        assertThat(servicio.buscarPorUsername("Ana.Torres").getIdUsuario()).isEqualTo("USR-0001");
    }

    @Test
    void buscarPorUsernameInexistenteLanzaNotFound() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.buscarPorUsername("Nadie"))
                .withMessage("No se encontró el usuario nadie.");
    }
    // ------------------------------------------------------------ listar y obtener

    @Test
    void listarDevuelveActivosEInactivosYFiltraPorIdUsernameOTrabajador() {
        Doctor doctor = (Doctor) trabajadorService.registrarTrabajador(
                "Carlos Mena", "Doctor", "Cardiologia", null);
        Usuario admin = servicio.crearUsuario("admin.uno", PASSWORD_VALIDA, Rol.ADMIN, null);
        Usuario medico = servicio.crearUsuario("carlos.mena", PASSWORD_VALIDA, Rol.DOCTOR,
                doctor.getIdTrabajador());
        servicio.crearUsuario("admin.dos", PASSWORD_VALIDA, Rol.ADMIN, null);
        servicio.desactivar(medico.getIdUsuario(), "admin.uno");

        assertThat(servicio.listar(null)).hasSize(3);
        assertThat(servicio.listar("  CARLOS ")).containsExactly(medico);
        assertThat(servicio.listar("doc-0001")).containsExactly(medico);
        assertThat(servicio.listar(admin.getIdUsuario())).containsExactly(admin);
    }

    @Test
    void obtenerUnUsuarioInexistenteLanzaNotFound() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.obtener("USR-0099"))
                .withMessage("No se encontró el usuario USR-0099.");
    }

    // -------------------------------------------------------------- desactivar

    @Test
    void desactivarEsIdempotente() {
        servicio.crearUsuario("admin.uno", PASSWORD_VALIDA, Rol.ADMIN, null);
        Usuario otro = servicio.crearUsuario("admin.dos", PASSWORD_VALIDA, Rol.ADMIN, null);

        servicio.desactivar(otro.getIdUsuario(), "admin.uno");
        Usuario otraVez = servicio.desactivar(otro.getIdUsuario(), "admin.uno");

        assertThat(otraVez.isActivo()).isFalse();
    }

    @Test
    void nadiePuedeDesactivarseASiMismo() {
        Usuario admin = servicio.crearUsuario("admin.uno", PASSWORD_VALIDA, Rol.ADMIN, null);
        servicio.crearUsuario("admin.dos", PASSWORD_VALIDA, Rol.ADMIN, null);

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.desactivar(admin.getIdUsuario(), "ADMIN.UNO"))
                .withMessage("No puede desactivar su propio usuario.");
        assertThat(admin.isActivo()).isTrue();
    }

    @Test
    void noSePuedeDesactivarAlUltimoAdministradorActivo() {
        Usuario uno = servicio.crearUsuario("admin.uno", PASSWORD_VALIDA, Rol.ADMIN, null);
        Usuario dos = servicio.crearUsuario("admin.dos", PASSWORD_VALIDA, Rol.ADMIN, null);
        Usuario tres = servicio.crearUsuario("admin.tres", PASSWORD_VALIDA, Rol.ADMIN, null);
        servicio.desactivar(dos.getIdUsuario(), "admin.uno");
        servicio.desactivar(tres.getIdUsuario(), "admin.uno");

        // admin.uno es el último activo: nadie (ni otro solicitante) puede desactivarlo.
        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.desactivar(uno.getIdUsuario(), "admin.dos"))
                .withMessage("No se puede desactivar al último administrador activo.");
        assertThat(uno.isActivo()).isTrue();
    }

    // La desactivación simultánea de dos administradores (invariante del último
    // administrador bajo concurrencia) se prueba contra MongoDB real en
    // UsuarioConcurrenciaIntegrationTest: el bloqueo es de la base de datos.

    @Test
    void desactivarUnUsuarioInexistenteLanzaNotFound() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.desactivar("USR-0099", "admin"));
    }

    // ----------------------------------------------------------------- activar

    @Test
    void activarEsIdempotenteYReactivaAUnDoctorConSuTrabajadorVigente() {
        Doctor doctor = (Doctor) trabajadorService.registrarTrabajador(
                "Carlos Mena", "Doctor", "Cardiologia", null);
        servicio.crearUsuario("admin.uno", PASSWORD_VALIDA, Rol.ADMIN, null);
        Usuario medico = servicio.crearUsuario("carlos.mena", PASSWORD_VALIDA, Rol.DOCTOR,
                doctor.getIdTrabajador());

        assertThat(servicio.activar(medico.getIdUsuario()).isActivo()).isTrue();
        servicio.desactivar(medico.getIdUsuario(), "admin.uno");
        assertThat(servicio.activar(medico.getIdUsuario()).isActivo()).isTrue();
    }

    @Test
    void noSeReactivaAUnUsuarioCuyoTrabajadorYaNoExiste() {
        Doctor doctor = (Doctor) trabajadorService.registrarTrabajador(
                "Carlos Mena", "Doctor", "Cardiologia", null);
        servicio.crearUsuario("admin.uno", PASSWORD_VALIDA, Rol.ADMIN, null);
        Usuario medico = servicio.crearUsuario("carlos.mena", PASSWORD_VALIDA, Rol.DOCTOR,
                doctor.getIdTrabajador());
        servicio.desactivar(medico.getIdUsuario(), "admin.uno");
        trabajadorService.eliminarTrabajador(doctor.getIdTrabajador());

        assertThatExceptionOfType(ConflictoException.class)
                .isThrownBy(() -> servicio.activar(medico.getIdUsuario()))
                .withMessage("No se puede activar el usuario carlos.mena: su trabajador DOC-0001 "
                        + "ya no existe.");
        assertThat(medico.isActivo()).isFalse();
    }

    @Test
    void unAdministradorSeReactivaSinComprobarTrabajador() {
        servicio.crearUsuario("admin.uno", PASSWORD_VALIDA, Rol.ADMIN, null);
        Usuario dos = servicio.crearUsuario("admin.dos", PASSWORD_VALIDA, Rol.ADMIN, null);
        servicio.desactivar(dos.getIdUsuario(), "admin.uno");

        assertThat(servicio.activar(dos.getIdUsuario()).isActivo()).isTrue();
    }

    // ------------------------------------------------------ restablecer contraseña

    @Test
    void restablecerPasswordSustituyeElHashYLaAnteriorDejaDeValer() {
        Usuario admin = servicio.crearUsuario("admin.uno", PASSWORD_VALIDA, Rol.ADMIN, null);
        String hashAnterior = admin.getPasswordHash();

        servicio.restablecerPassword(admin.getIdUsuario(), "nueva-clave-segura");

        assertThat(admin.getPasswordHash()).startsWith("$2").isNotEqualTo(hashAnterior);
        assertThat(encoder.matches("nueva-clave-segura", admin.getPasswordHash())).isTrue();
        assertThat(encoder.matches(PASSWORD_VALIDA, admin.getPasswordHash())).isFalse();
    }

    @Test
    void restablecerPasswordAplicaLaPoliticaSinRevelarLaPassword() {
        Usuario admin = servicio.crearUsuario("admin.principal", PASSWORD_VALIDA, Rol.ADMIN, null);
        String hashAnterior = admin.getPasswordHash();

        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.restablecerPassword(admin.getIdUsuario(), "corta-123"))
                .withMessage("La contraseña debe tener al menos 10 caracteres.")
                .satisfies(e -> assertThat(e.getMessage()).doesNotContain("corta-123"));
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.restablecerPassword(admin.getIdUsuario(), "ñ".repeat(37)))
                .withMessageContaining("72 bytes");
        assertThatExceptionOfType(SolicitudInvalidaException.class)
                .isThrownBy(() -> servicio.restablecerPassword(admin.getIdUsuario(), "ADMIN.PRINCIPAL"))
                .withMessage("La contraseña no puede ser igual al username.");
        assertThat(admin.getPasswordHash()).isEqualTo(hashAnterior);
    }

    @Test
    void restablecerPasswordDeUnUsuarioInexistenteLanzaNotFound() {
        assertThatExceptionOfType(NotFoundException.class)
                .isThrownBy(() -> servicio.restablecerPassword("USR-0099", "nueva-clave-segura"));
    }
}
