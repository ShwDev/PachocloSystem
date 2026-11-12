package com.pachoclosystem.medicamentos.repository;

import com.pachoclosystem.medicamentos.dto.MedicamentoResponse;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

/**
 * Claves de idempotencia en la colección {@code idempotencia_salidas}, una por
 * salida hecha ({@code _id} es la clave).
 *
 * <p>No hay bloqueo por clave: dos peticiones simultáneas con la misma clave
 * hacen su descuento en su propia transacción y luego compiten por el
 * documento; la que llega segunda no puede insertar (el índice único del
 * {@code _id} lo impide), recibe {@link SalidaYaVigente} y su transacción se
 * aborta (su descuento se deshace). Al reintentar ya encuentra la salida de
 * la primera y responde con ella, sin descontar dos veces.</p>
 */
@Repository
public class IdempotenciaSalidasRepositoryMongo implements IdempotenciaSalidasRepository {

    private final MongoTemplate mongoTemplate;

    public IdempotenciaSalidasRepositoryMongo(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<SalidaRegistrada> buscarVigente(String clave, Instant limite) {
        Query query = new Query(Criteria.where("_id").is(clave).and("hechaEn").gt(limite));
        IdempotenciaSalidaDocument doc = mongoTemplate.findOne(query, IdempotenciaSalidaDocument.class);
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(new SalidaRegistrada(
                doc.getIdMedicamento(), doc.getCantidad(), doc.getRespuesta(), doc.getHechaEn()
        ));
    }

    @Override
    public void guardar(String clave, String idMedicamento, int cantidad, MedicamentoResponse respuesta,
                        Instant hechaEn, Instant limite) {
        // 1. Sustituir solo una clave caducada (hechaEn anterior o igual a
        //    limite): la sustitución y su condición son la misma operación.
        Query caducada = new Query(Criteria.where("_id").is(clave).and("hechaEn").lte(limite));
        Update sustituir = new Update()
                .set("idMedicamento", idMedicamento)
                .set("cantidad", cantidad)
                .set("respuesta", respuesta)
                .set("hechaEn", hechaEn);
        if (mongoTemplate.updateFirst(caducada, sustituir, IdempotenciaSalidaDocument.class)
                .getMatchedCount() > 0) {
            return;
        }

        // 2. No había clave (o no era reemplazable): insertar. Si otra petición
        //    con la misma clave la insertó antes, el índice único del _id lo
        //    impide y se marca la carrera.
        try {
            mongoTemplate.insert(new IdempotenciaSalidaDocument(clave, idMedicamento, cantidad, respuesta, hechaEn));
        } catch (DuplicateKeyException ocupada) {
            throw new SalidaYaVigente("La clave de idempotencia ya tiene una salida vigente.");
        }
    }

    @Override
    public int purgarCaducadas(Instant limite) {
        Query query = new Query(Criteria.where("hechaEn").lte(limite));
        return (int) mongoTemplate.remove(query, IdempotenciaSalidaDocument.class).getDeletedCount();
    }

    @Document(collection = "idempotencia_salidas")
    public static class IdempotenciaSalidaDocument {
        @Id
        private String clave;
        private String idMedicamento;
        private int cantidad;
        private MedicamentoResponse respuesta;

        @Indexed
        private Instant hechaEn;

        public IdempotenciaSalidaDocument() {}

        public IdempotenciaSalidaDocument(String clave, String idMedicamento, int cantidad, MedicamentoResponse respuesta, Instant hechaEn) {
            this.clave = clave;
            this.idMedicamento = idMedicamento;
            this.cantidad = cantidad;
            this.respuesta = respuesta;
            this.hechaEn = hechaEn;
        }

        public String getClave() { return clave; }
        public String getIdMedicamento() { return idMedicamento; }
        public int getCantidad() { return cantidad; }
        public MedicamentoResponse getRespuesta() { return respuesta; }
        public Instant getHechaEn() { return hechaEn; }
    }
}
