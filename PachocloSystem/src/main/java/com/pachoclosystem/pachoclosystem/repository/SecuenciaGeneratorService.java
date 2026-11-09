package com.pachoclosystem.pachoclosystem.repository;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

@Service
public class SecuenciaGeneratorService {

    private final MongoTemplate mongoTemplate;

    public SecuenciaGeneratorService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public long obtenerSiguienteSecuencia(String nombreSecuencia) {
        Query query = new Query(Criteria.where("_id").is(nombreSecuencia));
        Update update = new Update().inc("seq", 1);
        FindAndModifyOptions options = FindAndModifyOptions.options().returnNew(true).upsert(true);

        ContadorSecuencia contador = mongoTemplate.findAndModify(query, update, options, ContadorSecuencia.class);
        return contador != null ? contador.getSeq() : 1;
    }

    @Document(collection = "secuencias")
    public static class ContadorSecuencia {
        @Id
        private String id;
        private long seq;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public long getSeq() { return seq; }
        public void setSeq(long seq) { this.seq = seq; }
    }
}