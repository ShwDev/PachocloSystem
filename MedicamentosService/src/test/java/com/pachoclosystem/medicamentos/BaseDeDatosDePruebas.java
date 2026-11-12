package com.pachoclosystem.medicamentos;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Vacía la base de datos de los tests. Antes comprueba que es una base de
 * pruebas (su nombre termina en {@code _test}): si por error apuntara a la de
 * desarrollo o a otra con datos reales, se detiene sin tocar nada.
 *
 * <p>Borra los documentos con {@code remove}, no con {@code dropCollection}:
 * así los índices (incluido el único de la clave del medicamento) se conservan
 * y no hay que recrearlos.</p>
 */
public final class BaseDeDatosDePruebas {

    private BaseDeDatosDePruebas() {
    }

    /** Borra todos los medicamentos, claves de idempotencia y secuencias (los ids vuelven a MED-0001). */
    public static void vaciar(MongoTemplate mongoTemplate) {
        comprobarQueEsDePruebas(mongoTemplate);
        for (String coleccion : new String[]{"medicamentos", "idempotencia_salidas", "secuencias"}) {
            mongoTemplate.remove(new Query(), coleccion);
        }
    }

    /** Falla si la base de {@code mongoTemplate} no es una base de pruebas. */
    public static void comprobarQueEsDePruebas(MongoTemplate mongoTemplate) {
        String base = mongoTemplate.getDb().getName();
        if (base == null || !base.endsWith("_test")) {
            throw new IllegalStateException("Los tests solo vacían bases cuyo nombre termina en \"_test\"; "
                    + "esta es \"" + base + "\". Revise spring.mongodb.uri en los tests.");
        }
    }
}
