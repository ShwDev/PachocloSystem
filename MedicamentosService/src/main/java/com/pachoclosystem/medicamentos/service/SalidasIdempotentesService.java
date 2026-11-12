package com.pachoclosystem.medicamentos.service;

import com.pachoclosystem.medicamentos.dto.MedicamentoResponse;
import com.pachoclosystem.medicamentos.exception.ConflictoException;
import com.pachoclosystem.medicamentos.exception.SolicitudInvalidaException;
import com.pachoclosystem.medicamentos.model.Medicamento;
import com.pachoclosystem.medicamentos.repository.IdempotenciaSalidasRepository;
import com.mongodb.MongoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Salidas de stock con clave de idempotencia ({@code Idempotency-Key}): repetir
 * la petición con la misma clave y los mismos datos devuelve la misma
 * respuesta sin volver a descontar. Así quien llama (PachocloSystem) puede
 * reintentar tras un tiempo de espera sin riesgo de descontar dos veces.
 *
 * <ul>
 *   <li>Misma clave con otro medicamento u otra cantidad: 409.</li>
 *   <li>Dos peticiones simultáneas con la misma clave: la segunda no puede
 *       registrar la clave (el índice único del id de la clave lo impide),
 *       aborta su transacción —su descuento se deshace— y, al reintentarla,
 *       ya encuentra la salida de la primera y recibe su respuesta.</li>
 *   <li>La salida y el registro de la clave van en la misma transacción. Una
 *       salida que falla (stock insuficiente, vencido, inexistente) no deja
 *       nada guardado, así que al reintentarla se vuelve a evaluar: puede salir
 *       bien si entre tanto entró stock, o volver a fallar.</li>
 *   <li>MongoDB puede rechazar una transacción porque otra escribió a la vez
 *       alguno de sus documentos (conflicto de escritura): se reintenta la
 *       transacción entera —nunca se descuenta dos veces— durante unos
 *       segundos antes de devolver el error.</li>
 *   <li>Cada clave caduca a las {@code medicamentos.idempotencia.caducidad} (24 h
 *       por defecto) de hacerse la salida; después se trata como nueva. Las
 *       caducadas se borran al registrar salidas. Las claves sobreviven a un
 *       reinicio del servicio.</li>
 * </ul>
 *
 * <p>Sin clave, la salida es la de {@link MedicamentoService#registrarSalida}.</p>
 */
@Service
public class SalidasIdempotentesService {

    /** Letras, dígitos, guion y guion bajo; de 16 a 100 (un UUID tiene 36). */
    private static final Pattern PATRON_CLAVE = Pattern.compile("^[A-Za-z0-9_-]{16,100}$");

    /** Presupuesto de reintentos de una transacción con conflicto de escritura. */
    private static final Duration TOPE_REINTENTOS = Duration.ofSeconds(10);
    private static final Duration PAUSA_ENTRE_REINTENTOS = Duration.ofMillis(25);

    /** Respuesta de la salida y si es la repetición de una ya hecha. */
    public record Resultado(MedicamentoResponse respuesta, boolean repetida) {
    }

    private final MedicamentoService medicamentos;
    private final IdempotenciaSalidasRepository claves;
    private final TransactionOperations transacciones;
    private final Clock reloj;
    private final Duration caducidad;

    /** Sin Spring (tests unitarios): sin transacción y con el reloj dado. */
    public SalidasIdempotentesService(MedicamentoService medicamentos, IdempotenciaSalidasRepository claves,
                                       Clock reloj, Duration caducidad) {
        this(medicamentos, claves, TransactionOperations.withoutTransaction(), reloj, caducidad);
    }

    @Autowired
    public SalidasIdempotentesService(MedicamentoService medicamentos, IdempotenciaSalidasRepository claves,
                                       TransactionOperations transacciones, Clock reloj,
                                       @Value("${medicamentos.idempotencia.caducidad:24h}") Duration caducidad) {
        if (caducidad.isNegative() || caducidad.isZero()) {
            throw new IllegalStateException("La caducidad de las claves de idempotencia debe ser positiva.");
        }
        this.medicamentos = medicamentos;
        this.claves = claves;
        this.transacciones = transacciones;
        this.reloj = reloj;
        this.caducidad = caducidad;
    }

    public Resultado registrarSalida(String idMedicamento, int cantidad, String clave) {
        if (clave == null || !PATRON_CLAVE.matcher(clave).matches()) {
            throw new SolicitudInvalidaException("La cabecera Idempotency-Key debe tener entre 16 y 100 "
                    + "caracteres: letras sin tilde, dígitos, guion o guion bajo (por ejemplo, un UUID).");
        }
        long fin = System.nanoTime() + TOPE_REINTENTOS.toNanos();
        while (true) {
            try {
                return transacciones.execute(estado -> salidaEnTransaccion(idMedicamento, cantidad, clave));
            } catch (RuntimeException fallo) {
                if (!esReintentable(fallo) || System.nanoTime() > fin) {
                    throw fallo;
                }
                dormir(PAUSA_ENTRE_REINTENTOS);
            }
        }
    }

    private Resultado salidaEnTransaccion(String idMedicamento, int cantidad, String clave) {
        Instant ahora = reloj.instant();
        Instant limite = ahora.minus(caducidad);

        Optional<IdempotenciaSalidasRepository.SalidaRegistrada> previa = claves.buscarVigente(clave, limite);
        if (previa.isPresent()) {
            IdempotenciaSalidasRepository.SalidaRegistrada salida = previa.get();
            if (!salida.idMedicamento().equals(idMedicamento) || salida.cantidad() != cantidad) {
                throw new ConflictoException("La clave de idempotencia ya se usó para otra salida de "
                        + "stock con otro medicamento o cantidad.");
            }
            return new Resultado(salida.respuesta(), true);
        }

        // Misma transacción: si algo falla, la excepción deshace el descuento
        // y la clave queda libre.
        Medicamento medicamento = medicamentos.registrarSalida(idMedicamento, cantidad);
        MedicamentoResponse respuesta = MedicamentoResponse.from(medicamento, medicamentos.hoy());
        claves.purgarCaducadas(limite);
        claves.guardar(clave, idMedicamento, cantidad, respuesta, ahora, limite);
        return new Resultado(respuesta, false);
    }

    /**
     * Solo se reintenta lo que MongoDB marca como transitorio (etiqueta de
     * conflicto de escritura o de confirmación incierta, incluso dentro de una
     * excepción traducida por Spring) y la carrera de dos peticiones con la
     * misma clave; los errores de la petición (stock, 409, validación) suben
     * tal cual.
     */
    private static boolean esReintentable(Throwable fallo) {
        for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
            if (causa instanceof IdempotenciaSalidasRepository.SalidaYaVigente) {
                return true;
            }
            if (causa instanceof MongoException mongo && esTransitoria(mongo)) {
                return true;
            }
        }
        return false;
    }

    private static boolean esTransitoria(MongoException mongo) {
        for (String etiqueta : mongo.getErrorLabels()) {
            if ("TransientTransactionError".equals(etiqueta)
                    || "UnknownTransactionCommitResult".equals(etiqueta)) {
                return true;
            }
        }
        return false;
    }

    private static void dormir(Duration pausa) {
        try {
            Thread.sleep(pausa.toMillis());
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reintento de la salida interrumpido.", interrumpido);
        }
    }
}
