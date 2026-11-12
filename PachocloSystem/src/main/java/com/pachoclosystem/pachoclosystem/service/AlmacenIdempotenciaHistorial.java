package com.pachoclosystem.pachoclosystem.service;

import com.pachoclosystem.pachoclosystem.dto.RegistroResponse;
import com.pachoclosystem.pachoclosystem.repository.IHistorialIdempotenciaRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Supplier;

/**
 * Claves {@code Idempotency-Key} ya usadas al crear registros del historial. Se
 * guardan en MongoDB ({@link IHistorialIdempotenciaRepository}), así que
 * sobreviven a un reinicio; el cerrojo de cada clave está en la JVM (una sola
 * instancia de PachocloSystem).
 *
 * <ul>
 *   <li>Cada clave recuerda con qué usuario, paciente y cuerpo (huella) se usó y,
 *       si se llegó a crear, el registro. Sin registro significa que el intento
 *       no se pudo confirmar y se puede repetir con la misma petición.</li>
 *   <li>Cada entrada caduca a las {@code historial.idempotencia.caducidad} (24 h
 *       por defecto) de guardarse; al guardar se borran las caducadas. No hay
 *       tope de entradas.</li>
 *   <li>{@link #guardar} se une a la transacción en curso si la hay: así el
 *       registro y su clave se guardan juntos o no se guarda ninguno.</li>
 *   <li>{@link #conClave(String, Supplier)} ejecuta con el cerrojo de una clave:
 *       las peticiones con la misma clave van de una en una. El cerrojo es de esa
 *       clave sola (dura mientras haya peticiones con ella), así que una petición
 *       que espera a MedicamentosService no frena a las de otras claves.</li>
 * </ul>
 */
@Component
public class AlmacenIdempotenciaHistorial {

    /** Petición con la que se usó una clave; {@code registro} es null si no se pudo confirmar. */
    public record Uso(String idUsuario, String idPaciente, String huella, RegistroResponse registro,
                      Instant usadaEn) {

        public boolean esDe(String idUsuario, String idPaciente, String huella) {
            return this.idUsuario.equals(idUsuario) && this.idPaciente.equals(idPaciente)
                    && this.huella.equals(huella);
        }
    }

    /** Cerrojo de una clave y cuántas peticiones lo usan o esperan (protegido por el mapa). */
    private static final class Cerrojo {
        private final ReentrantLock bloqueo = new ReentrantLock();
        private volatile int peticiones;
    }

    private final IHistorialIdempotenciaRepository repositorio;
    private final Clock reloj;
    private final Duration caducidad;
    private final ConcurrentHashMap<String, Cerrojo> cerrojos = new ConcurrentHashMap<>();

    public AlmacenIdempotenciaHistorial(IHistorialIdempotenciaRepository repositorio, Clock reloj,
                                        @Value("${historial.idempotencia.caducidad:24h}") Duration caducidad) {
        if (caducidad.isNegative() || caducidad.isZero()) {
            throw new IllegalStateException("La caducidad de idempotencia del historial debe ser positiva.");
        }
        this.repositorio = repositorio;
        this.reloj = reloj;
        this.caducidad = caducidad;
    }

    /** Ejecuta {@code accion} con el cerrojo de la clave: nadie más con esa clave entra a la vez. */
    public <T> T conClave(String clave, Supplier<T> accion) {
        Cerrojo cerrojo = cerrojos.compute(clave, (k, actual) -> {
            Cerrojo c = actual == null ? new Cerrojo() : actual;
            c.peticiones++;
            return c;
        });
        cerrojo.bloqueo.lock();
        try {
            return accion.get();
        } finally {
            cerrojo.bloqueo.unlock();
            cerrojos.computeIfPresent(clave, (k, c) -> --c.peticiones == 0 ? null : c);
        }
    }

    /** El uso guardado de esa clave, si existe y no ha caducado. */
    public Optional<Uso> buscar(String clave) {
        return repositorio.buscarVigente(clave, limite())
                .map(u -> new Uso(u.idUsuario(), u.idPaciente(), u.huella(), u.registro(), u.usadaEn()));
    }

    /** Guarda (o sustituye) el uso de una clave; {@code registro} null = sin confirmar. */
    public void guardar(String clave, String idUsuario, String idPaciente, String huella,
                        RegistroResponse registro) {
        Instant ahora = reloj.instant().truncatedTo(ChronoUnit.MICROS);
        repositorio.guardar(clave, idUsuario, idPaciente, huella, registro, ahora);
        repositorio.purgarCaducadas(ahora.minus(caducidad));
    }

    /** Número de claves vigentes (para pruebas y diagnóstico). */
    public int tamano() {
        return Math.toIntExact(repositorio.contarVigentes(limite()));
    }

    /** Peticiones con esa clave en curso o esperando su turno (para pruebas y diagnóstico). */
    public int peticionesConClave(String clave) {
        Cerrojo cerrojo = cerrojos.get(clave);
        return cerrojo == null ? 0 : cerrojo.peticiones;
    }

    /**
     * Cerrojos vivos (para pruebas y diagnóstico): solo los de claves con
     * peticiones en curso o esperando. No dependen de la caducidad de la clave:
     * el de una clave se borra en cuanto termina su última petición.
     */
    public int cerrojosActivos() {
        return cerrojos.size();
    }

    /** Una clave usada en este instante o antes está caducada. */
    private Instant limite() {
        return reloj.instant().minus(caducidad);
    }
}
