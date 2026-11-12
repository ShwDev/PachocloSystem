package com.pachoclosystem.pachoclosystem;

import com.pachoclosystem.pachoclosystem.model.Rol;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.util.List;

/**
 * Vacía la base de datos de los tests. Antes comprueba que es una base de
 * pruebas (su nombre termina en {@code _test}): si por error apuntara a la de
 * desarrollo o a otra con datos reales, se detiene sin tocar nada.
 *
 * <p>Borra los documentos con {@code remove}, no con {@code dropCollection}:
 * así los índices (username y trabajador únicos) se conservan.</p>
 */
public final class BaseDeDatosDePruebas {

    /** Categorías que no se conservan entre pruebas (los usuarios se tratan aparte). */
    private static final List<String> COLECCIONES = List.of(
            "registros_clinicos", "historial_idempotencia", "pacientes", "trabajadores", "cerrojos");

    /** Todo lo que existe en la base de pruebas (para dejar un contexto recién arrancado limpio). */
    private static final List<String> TODAS = List.of(
            "registros_clinicos", "historial_idempotencia", "pacientes", "trabajadores", "cerrojos",
            "usuarios", "secuencias");

    /** Secuencias que se reinician en cada limpieza (las de usuario no: el admin conserva su id). */
    private static final List<String> SECUENCIAS_REINICIABLES = List.of(
            "pacientes_id_seq", "doctores_id_seq", "enfermeros_id_seq");

    private BaseDeDatosDePruebas() {
    }

    /** Deja la base completamente vacía (la usa el arranque limpio de los contextos de test). */
    public static void limpiar(MongoTemplate mongoTemplate) {
        comprobarQueEsDePruebas(mongoTemplate);
        for (String coleccion : TODAS) {
            mongoTemplate.remove(new Query(), coleccion);
        }
    }

    /**
     * Borra pacientes, trabajadores, registros, claves de idempotencia y todos los
     * usuarios salvo {@code usernameAdmin} (que vuelve a estar activo y con rol
     * ADMIN; su contraseña no se toca), y reinicia los ids de pacientes y
     * trabajadores (PAC-0001, DOC-0001, ENF-0001). Los ids de usuario no se
     * reinician: el administrador conserva el suyo.
     */
    public static void vaciar(MongoTemplate mongoTemplate, String usernameAdmin) {
        comprobarQueEsDePruebas(mongoTemplate);
        for (String coleccion : COLECCIONES) {
            mongoTemplate.remove(new Query(), coleccion);
        }
        mongoTemplate.remove(new Query(Criteria.where("username").ne(usernameAdmin)), "usuarios");
        mongoTemplate.updateFirst(new Query(Criteria.where("username").is(usernameAdmin)),
                new Update().set("activo", true).set("rol", Rol.ADMIN).set("idTrabajador", null), "usuarios");
        mongoTemplate.remove(new Query(Criteria.where("_id").in(SECUENCIAS_REINICIABLES)), "secuencias");
    }

    /**
     * Falla si la base de {@code mongoTemplate} no es una base de pruebas.
     */
    public static void comprobarQueEsDePruebas(MongoTemplate mongoTemplate) {
        String base = mongoTemplate.getDb().getName();
        if (base == null || !base.endsWith("_test")) {
            throw new IllegalStateException("Los tests solo vacían bases cuyo nombre termina en \"_test\"; "
                    + "esta es \"" + base + "\". Revise spring.mongodb.uri en los tests.");
        }
    }
}
