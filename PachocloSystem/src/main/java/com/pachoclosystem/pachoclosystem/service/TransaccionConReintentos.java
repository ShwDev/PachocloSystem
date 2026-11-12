package com.pachoclosystem.pachoclosystem.service;

import com.mongodb.MongoException;
import org.springframework.transaction.support.TransactionOperations;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * Reintentos de las transacciones de MongoDB.
 *
 * <p>MongoDB rechaza una transacción cuando otra escribió a la vez alguno de
 * sus documentos (conflicto de escritura, incluso entre dos peticiones que no
 * se tocan directamente pero comparten el cerrojo administrativo) y puede no
 * confirmar el resultado del commit. En ambos casos la transacción no dejó nada
 * a medias: se repite entera, y el reintento vuelve a leer los datos frescos,
 * así que las comprobaciones (último administrador, trabajador ocupado...) se
 * evalúan de nuevo. Los errores de la petición (404, 409, 400) no se
 * reintentan: suben tal cual.</p>
 *
 * <p>El bucle solo ve excepciones no comprobadas (lo que corre dentro de una
 * transacción de Spring); para una transacción sin conflicto se ejecuta una
 * sola vez.</p>
 */
final class TransaccionConReintentos {

    /** Presupuesto total de reintentos de una operación. */
    private static final Duration TOPE = Duration.ofSeconds(10);
    private static final long PAUSA_MINIMA_MS = 15;
    private static final long PAUSA_MAXIMA_MS = 60;

    private TransaccionConReintentos() {
    }

    static <T> T ejecutar(TransactionOperations transacciones, Supplier<T> trabajo) {
        long fin = System.nanoTime() + TOPE.toNanos();
        while (true) {
            try {
                return transacciones.execute(estado -> trabajo.get());
            } catch (RuntimeException fallo) {
                if (!esReintentable(fallo) || System.nanoTime() > fin) {
                    throw fallo;
                }
                dormir();
            }
        }
    }

    /**
     * Solo se reintenta lo que MongoDB marca como transitorio (conflicto de
     * escritura o confirmación incierta), también cuando la excepción viene
     * envuelta (por ejemplo, el fallo del commit).
     */
    private static boolean esReintentable(Throwable fallo) {
        for (Throwable causa = fallo; causa != null; causa = causa.getCause()) {
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

    private static void dormir() {
        long pausa = PAUSA_MINIMA_MS + (long) (Math.random() * (PAUSA_MAXIMA_MS - PAUSA_MINIMA_MS));
        try {
            Thread.sleep(pausa);
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Reintento de la transacción interrumpido.", interrumpido);
        }
    }
}
