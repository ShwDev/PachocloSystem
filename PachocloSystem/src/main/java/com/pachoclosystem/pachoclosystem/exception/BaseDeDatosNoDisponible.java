package com.pachoclosystem.pachoclosystem.exception;

import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.CannotCreateTransactionException;

/**
 * Fallos de la base de datos (MongoDB) que la API responde con 503 y un mensaje
 * genérico: sin conexión (base caída, servidor inalcanzable), sin poder abrir la
 * transacción o un fallo transitorio (conflicto de escritura, commit incierto).
 * Repetir la petición más tarde es seguro: la transacción se deshizo.
 */
public final class BaseDeDatosNoDisponible {

    public static final String MENSAJE =
            "El servicio no puede acceder a sus datos en este momento. Vuelva a intentarlo más tarde.";

    private BaseDeDatosNoDisponible() {
    }

    /** Si {@code error} o alguna de sus causas es uno de esos fallos. */
    public static boolean es(Throwable error) {
        for (Throwable actual = error; actual != null; actual = actual.getCause()) {
            if (actual instanceof DataAccessResourceFailureException
                    || actual instanceof CannotCreateTransactionException
                    || actual instanceof TransientDataAccessException) {
                return true;
            }
            if (actual.getCause() == actual) {
                break;
            }
        }
        return false;
    }
}
