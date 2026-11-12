package com.pachoclosystem.pachoclosystem.repository;

import com.pachoclosystem.pachoclosystem.dto.RegistroResponse;
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
public class HistorialIdempotenciaRepositoryMongo implements IHistorialIdempotenciaRepository {

    private final MongoTemplate mongoTemplate;

    public HistorialIdempotenciaRepositoryMongo(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public Optional<UsoGuardado> buscarVigente(String clave, Instant limite) {
        Query query = new Query(Criteria.where("_id").is(clave).and("usadaEn").gt(limite));
        IdempotenciaDocument doc = mongoTemplate.findOne(query, IdempotenciaDocument.class);
        if (doc == null) {
            return Optional.empty();
        }
        return Optional.of(new UsoGuardado(
                doc.getIdUsuario(), doc.getIdPaciente(), doc.getHuella(), doc.getRegistro(), doc.getUsadaEn()
        ));
    }

    @Override
    public void guardar(String clave, String idUsuario, String idPaciente, String huella, RegistroResponse registro, Instant usadaEn) {
        IdempotenciaDocument doc = new IdempotenciaDocument(clave, idUsuario, idPaciente, huella, registro, usadaEn);
        mongoTemplate.save(doc);
    }

    @Override
    public int purgarCaducadas(Instant limite) {
        Query query = new Query(Criteria.where("usadaEn").lte(limite));
        return (int) mongoTemplate.remove(query, IdempotenciaDocument.class).getDeletedCount();
    }

    @Override
    public long contarVigentes(Instant limite) {
        Query query = new Query(Criteria.where("usadaEn").gt(limite));
        return mongoTemplate.count(query, IdempotenciaDocument.class);
    }

    @Document(collection = "historial_idempotencia")
    public static class IdempotenciaDocument {
        @Id
        private String clave;
        private String idUsuario;
        private String idPaciente;
        private String huella;
        private RegistroResponse registro;

        @Indexed
        private Instant usadaEn;

        public IdempotenciaDocument() {}

        public IdempotenciaDocument(String clave, String idUsuario, String idPaciente, String huella, RegistroResponse registro, Instant usadaEn) {
            this.clave = clave;
            this.idUsuario = idUsuario;
            this.idPaciente = idPaciente;
            this.huella = huella;
            this.registro = registro;
            this.usadaEn = usadaEn;
        }

        public String getClave() { return clave; }
        public String getIdUsuario() { return idUsuario; }
        public String getIdPaciente() { return idPaciente; }
        public String getHuella() { return huella; }
        public RegistroResponse getRegistro() { return registro; }
        public Instant getUsadaEn() { return usadaEn; }
    }
}