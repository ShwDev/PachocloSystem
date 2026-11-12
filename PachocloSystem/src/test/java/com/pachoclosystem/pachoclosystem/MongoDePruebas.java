package com.pachoclosystem.pachoclosystem;

import org.testcontainers.mongodb.MongoDBContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * MongoDB real para los tests: un único contenedor por ejecución, con réplica
 * de un nodo (necesaria para las transacciones) y publicado en un puerto fijo
 * para que la URI de {@code src/test/resources/config/application.properties}
 * sea estática.
 *
 * <p>Lo arranca {@link MongoDePruebasListener} al abrir la sesión de JUnit, es
 * decir, antes de que se cree ningún contexto de Spring. Se para al terminar
 * (y Testcontainers lo limpia si la JVM muere).</p>
 */
public final class MongoDePruebas {

    /** Puerto fijo en el equipo; distinto del de MedicamentosService para poder correr ambos a la vez. */
    public static final int PUERTO = 27518;

    private static MongoDBContainer contenedor;

    private MongoDePruebas() {
    }

    public static synchronized void iniciar() {
        if (contenedor != null) {
            return;
        }
        Contenedor nuevo = new Contenedor();
        nuevo.start();
        contenedor = nuevo;
        Runtime.getRuntime().addShutdownHook(new Thread(MongoDePruebas::parar));
    }

    private static synchronized void parar() {
        if (contenedor != null) {
            contenedor.stop();
            contenedor = null;
        }
    }

    private static final class Contenedor extends MongoDBContainer {

        Contenedor() {
            super(DockerImageName.parse("mongo:8"));
            addFixedExposedPort(PUERTO, 27017);
            // La URI de los tests usa directConnection=true: el miembro del
            // replica set anuncia su host interno, que no es alcanzable desde
            // el equipo; la conexión directa no lo sigue y las transacciones
            // funcionan igual contra el primario.
            withReplicaSet();
        }
    }
}
