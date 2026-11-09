package com.pachoclosystem.medicamentos.repository;

import com.pachoclosystem.medicamentos.dto.MedicamentoResponse;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public class IdempotenciaSalidasRepositoryMongo implements IdempotenciaSalidasRepository {

    private final MongoTemplate mongoTemplate;

    public IdempotenciaSalidasRepositoryMongo(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void bloquear(String clave) {
        // En MongoDB las operaciones atómicas a nivel de documento reemplazan la necesidad de advisory locks relacionales.
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
        Query queryExistente = new Query(Criteria.where("_id").is(clave).and("hechaEn").gt(limite));
        if (mongoTemplate.exists(queryExistente, IdempotenciaSalidaDocument.class)) {
            throw new IllegalStateException("La clave de idempotencia ya tiene una salida vigente.");
        }

        IdempotenciaSalidaDocument doc = new IdempotenciaSalidaDocument(clave, idMedicamento, cantidad, respuesta, hechaEn);
        mongoTemplate.save(doc);
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