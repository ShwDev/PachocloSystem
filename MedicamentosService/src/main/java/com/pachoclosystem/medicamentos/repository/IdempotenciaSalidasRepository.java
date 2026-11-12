package com.pachoclosystem.medicamentos.repository;

import com.pachoclosystem.medicamentos.dto.MedicamentoResponse;

import java.time.Instant;
import java.util.Optional;

/**
 * Salidas de stock ya hechas, por clave de idempotencia. La salida y el
 * registro de su clave van en la misma transacción, empezando por
 * {@link #buscarVigente(String, Instant)}.
 */
public interface IdempotenciaSalidasRepository {

    /** Lo que se recuerda de una salida hecha con una clave. */
    record SalidaRegistrada(String idMedicamento, int cantidad, MedicamentoResponse respuesta, Instant hechaEn) {
    }

    /** La salida guardada con esa clave si se hizo después de {@code limite} (no ha caducado). */
    Optional<SalidaRegistrada> buscarVigente(String clave, Instant limite);

    /**
     * Guarda la salida. Si la clave ya existía pero había caducado (anterior o
     * igual a {@code limite}), la sustituye; si seguía vigente, falla con
     * {@link SalidaYaVigente}: otra petición con la misma clave se adelantó y
     * quien llama debe abortar la transacción (deshacer su descuento) y
     * volver a empezar; al releer la clave ya está vigente y recibe esa
     * respuesta.
     */
    void guardar(String clave, String idMedicamento, int cantidad, MedicamentoResponse respuesta,
                 Instant hechaEn, Instant limite);

    /** Borra las claves caducadas (hechas antes o en {@code limite}). */
    int purgarCaducadas(Instant limite);

    /**
     * Marcador de que la clave ya tenía una salida vigente al intentar
     * guardar: no es un error de la petición sino una carrera con otra
     * petición simultánea, así que la transacción entera se reintenta.
     */
    class SalidaYaVigente extends IllegalStateException {

        public SalidaYaVigente(String mensaje) {
            super(mensaje);
        }
    }
}
