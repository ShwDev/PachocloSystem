package com.pachoclosystem.pachoclosystem.repository;

import com.pachoclosystem.pachoclosystem.dto.RegistroResponse;

import java.time.Instant;
import java.util.Optional;

/**
 * Claves {@code Idempotency-Key} usadas al crear registros del historial. La
 * implementación de la aplicación es {@link HistorialIdempotenciaRepositoryMongo}
 * (MongoDB). Una clave está vigente si se usó después de {@code limite}
 * (ahora menos la caducidad).
 */
public interface IHistorialIdempotenciaRepository {

    /** Uso guardado de una clave; {@code registro} es null si el intento no se pudo confirmar. */
    record UsoGuardado(String idUsuario, String idPaciente, String huella, RegistroResponse registro,
                       Instant usadaEn) {
    }

    /** El uso de la clave si se guardó después de {@code limite}. */
    Optional<UsoGuardado> buscarVigente(String clave, Instant limite);

    /** Guarda o sustituye el uso de la clave. */
    void guardar(String clave, String idUsuario, String idPaciente, String huella, RegistroResponse registro,
                 Instant usadaEn);

    /** Borra las claves usadas en {@code limite} o antes; devuelve cuántas. */
    int purgarCaducadas(Instant limite);

    /** Claves usadas después de {@code limite}. */
    long contarVigentes(Instant limite);
}
