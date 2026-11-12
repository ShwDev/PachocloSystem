package com.pachoclosystem.pachoclosystem.service;

import com.pachoclosystem.pachoclosystem.dto.RegistroResponse;
import com.pachoclosystem.pachoclosystem.model.TipoRegistro;
import com.pachoclosystem.pachoclosystem.repository.HistorialIdempotenciaRepositoryEnMemoria;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

/**
 * Caducidad y cerrojos del almacén de claves del historial, con el estado en un
 * doble en memoria. El estado en MongoDB lo prueba
 * {@code RepositoriosMongoTest}.
 */
class AlmacenIdempotenciaHistorialTest {

    /** Reloj que avanza a mano. */
    private static final class RelojManual extends Clock {
        private Instant ahora = Instant.parse("2026-06-15T10:00:00Z");

        void avanzar(Duration tiempo) {
            ahora = ahora.plus(tiempo);
        }

        @Override
        public Instant instant() {
            return ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }
    }

    private static final RegistroResponse REGISTRO = new RegistroResponse("reg-1", "PAC-0001", "Ana Torres",
            LocalDateTime.of(2026, 6, 15, 10, 0), TipoRegistro.EVOLUCION, null, "Paciente estable", null);

    private final RelojManual reloj = new RelojManual();

    private AlmacenIdempotenciaHistorial almacen(Duration caducidad) {
        return new AlmacenIdempotenciaHistorial(new HistorialIdempotenciaRepositoryEnMemoria(), reloj, caducidad);
    }

    @Test
    void guardaYDevuelveElUso() {
        AlmacenIdempotenciaHistorial almacen = almacen(Duration.ofHours(24));

        almacen.guardar("clave-uno-0000000", "USR-0001", "PAC-0001", "huella", REGISTRO);

        assertThat(almacen.buscar("clave-uno-0000000")).hasValueSatisfying(uso -> {
            assertThat(uso.esDe("USR-0001", "PAC-0001", "huella")).isTrue();
            assertThat(uso.esDe("USR-0002", "PAC-0001", "huella")).isFalse();
            assertThat(uso.esDe("USR-0001", "PAC-0002", "huella")).isFalse();
            assertThat(uso.esDe("USR-0001", "PAC-0001", "otra")).isFalse();
            assertThat(uso.registro()).isEqualTo(REGISTRO);
        });
        assertThat(almacen.buscar("otra-clave-000000")).isEmpty();
    }

    @Test
    void unUsoSinConfirmarSeSustituyeAlCrearElRegistro() {
        AlmacenIdempotenciaHistorial almacen = almacen(Duration.ofHours(24));

        almacen.guardar("clave-uno-0000000", "USR-0001", "PAC-0001", "huella", null);
        assertThat(almacen.buscar("clave-uno-0000000").orElseThrow().registro()).isNull();

        almacen.guardar("clave-uno-0000000", "USR-0001", "PAC-0001", "huella", REGISTRO);
        assertThat(almacen.buscar("clave-uno-0000000").orElseThrow().registro()).isEqualTo(REGISTRO);
        assertThat(almacen.tamano()).isEqualTo(1);
    }

    @Test
    void unaClaveCaducaALas24Horas() {
        AlmacenIdempotenciaHistorial almacen = almacen(Duration.ofHours(24));
        almacen.guardar("clave-uno-0000000", "USR-0001", "PAC-0001", "huella", REGISTRO);

        reloj.avanzar(Duration.ofHours(24).minusSeconds(1));
        assertThat(almacen.buscar("clave-uno-0000000")).isPresent();

        reloj.avanzar(Duration.ofSeconds(1));
        assertThat(almacen.buscar("clave-uno-0000000")).isEmpty();
        assertThat(almacen.tamano()).isZero();
    }

    @Test
    void sinTopeDeEntradasConservaTodasLasVigentes() {
        AlmacenIdempotenciaHistorial almacen = almacen(Duration.ofHours(24));

        for (int i = 1; i <= 50; i++) {
            almacen.guardar("clave-" + i + "-0000000000", "USR-0001", "PAC-0001", "huella-" + i, REGISTRO);
            reloj.avanzar(Duration.ofMinutes(1));
        }

        assertThat(almacen.tamano()).isEqualTo(50);
        assertThat(almacen.buscar("clave-1-0000000000")).isPresent();
        assertThat(almacen.buscar("clave-50-0000000000")).isPresent();
    }

    @Test
    void alGuardarSePurganLasCaducadasAunqueNoSeConsulten() {
        AlmacenIdempotenciaHistorial almacen = almacen(Duration.ofHours(1));
        almacen.guardar("vieja-1-0000000000", "USR-0001", "PAC-0001", "huella", REGISTRO);
        almacen.guardar("vieja-2-0000000000", "USR-0001", "PAC-0001", "huella", REGISTRO);

        reloj.avanzar(Duration.ofHours(2));
        almacen.guardar("nueva-0000000000000", "USR-0001", "PAC-0001", "huella", REGISTRO);

        assertThat(almacen.tamano()).isEqualTo(1);
    }

    @Test
    void laMismaClaveEsperaYOtraClaveNo() throws Exception {
        AlmacenIdempotenciaHistorial almacen = almacen(Duration.ofHours(24));
        CountDownLatch dentro = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);

        CompletableFuture<String> primera = CompletableFuture.supplyAsync(() ->
                almacen.conClave("clave-uno-0000000", () -> {
                    dentro.countDown();
                    esperar(soltar);
                    return "primera";
                }));
        assertThat(dentro.await(5, TimeUnit.SECONDS)).isTrue();

        // Otra clave entra aunque la primera siga dentro.
        assertThat(almacen.conClave("clave-dos-0000000", () -> "otra")).isEqualTo("otra");

        // La misma clave espera su turno.
        CompletableFuture<String> segunda = CompletableFuture.supplyAsync(() ->
                almacen.conClave("clave-uno-0000000", () -> "segunda"));
        esperarHasta(() -> almacen.peticionesConClave("clave-uno-0000000") == 2);
        assertThat(segunda).isNotDone();

        soltar.countDown();
        assertThat(primera.get(5, TimeUnit.SECONDS)).isEqualTo("primera");
        assertThat(segunda.get(5, TimeUnit.SECONDS)).isEqualTo("segunda");
        // Sin peticiones, el cerrojo de la clave desaparece.
        assertThat(almacen.peticionesConClave("clave-uno-0000000")).isZero();
    }

    @Test
    void elCerrojoSeSueltaAunqueLaAccionFalle() {
        AlmacenIdempotenciaHistorial almacen = almacen(Duration.ofHours(24));

        try {
            almacen.conClave("clave-uno-0000000", () -> {
                throw new IllegalStateException("fallo");
            });
        } catch (IllegalStateException esperado) {
            // la excepción sale tal cual
        }

        assertThat(almacen.peticionesConClave("clave-uno-0000000")).isZero();
        assertThat(almacen.cerrojosActivos()).isZero();
        assertThat(almacen.conClave("clave-uno-0000000", () -> "despues")).isEqualTo("despues");
    }

    @Test
    void losCerrojosSeLiberanAlTerminarYNoQuedanTrasCaducarLaClave() {
        AlmacenIdempotenciaHistorial almacen = almacen(Duration.ofHours(24));

        for (int i = 1; i <= 50; i++) {
            String clave = "clave-" + i + "-0000000000";
            almacen.conClave(clave, () -> {
                almacen.guardar(clave, "USR-0001", "PAC-0001", "huella", REGISTRO);
                return null;
            });
        }
        // Las claves se guardan, pero ningún cerrojo sobrevive a su petición.
        assertThat(almacen.tamano()).isEqualTo(50);
        assertThat(almacen.cerrojosActivos()).isZero();

        // Tras caducar, la clave es nueva y su cerrojo vuelve a crearse y a borrarse.
        reloj.avanzar(Duration.ofHours(24));
        String clave = "clave-50-0000000000";
        assertThat(almacen.conClave(clave, () -> {
            assertThat(almacen.cerrojosActivos()).isEqualTo(1);
            assertThat(almacen.buscar(clave)).isEmpty();
            almacen.guardar(clave, "USR-0002", "PAC-0002", "otra-huella", REGISTRO);
            return almacen.buscar(clave).orElseThrow().idUsuario();
        })).isEqualTo("USR-0002");
        assertThat(almacen.cerrojosActivos()).isZero();
        // Al guardar se purgaron las demás caducadas.
        assertThat(almacen.tamano()).isEqualTo(1);
    }

    @Test
    void rechazaUnaConfiguracionSinSentido() {
        assertThatIllegalStateException().isThrownBy(() -> almacen(Duration.ZERO));
        assertThatIllegalStateException().isThrownBy(() -> almacen(Duration.ofHours(-1)));
    }

    private static void esperar(CountDownLatch senal) {
        try {
            if (!senal.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("La prueba no soltó el cerrojo a tiempo.");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }

    private static void esperarHasta(BooleanSupplier condicion) throws InterruptedException {
        long limite = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (!condicion.getAsBoolean()) {
            if (System.nanoTime() > limite) {
                throw new AssertionError("La condición no se cumplió en 5 s.");
            }
            Thread.sleep(10);
        }
    }
}
