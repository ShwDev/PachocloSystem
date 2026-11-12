package com.pachoclosystem.pachoclosystem.repository;

import com.pachoclosystem.pachoclosystem.model.Paciente;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class PacienteRepositoryMongo implements IPacienteRepository {

    private final MongoTemplate mongoTemplate;
    private final SecuenciaGeneratorService secuenciaService;

    public PacienteRepositoryMongo(MongoTemplate mongoTemplate, SecuenciaGeneratorService secuenciaService) {
        this.mongoTemplate = mongoTemplate;
        this.secuenciaService = secuenciaService;
    }

    @Override
    public String generarNuevoId() {
        long numero = secuenciaService.obtenerSiguienteSecuencia("pacientes_id_seq");
        return String.format("PAC-%04d", numero);
    }

    @Override
    public Paciente buscarPorId(String id) {
        if (id == null) {
            return null;
        }
        return mongoTemplate.findById(id, Paciente.class);
    }

    @Override
    public boolean guardarPaciente(Paciente p) {
        if (p == null || p.getIdPaciente() == null) {
            return false;
        }
        try {
            mongoTemplate.insert(p);
            return true;
        } catch (DuplicateKeyException idOcupado) {
            return false;
        }
    }

    @Override
    public boolean actualizarDatos(String idPaciente, String nombre, int edad, int habitacion) {
        Query query = new Query(Criteria.where("_id").is(idPaciente).and("activo").is(true));
        Update update = new Update()
                .set("nombre", nombre)
                .set("edad", edad)
                .set("habitacion", habitacion);

        return mongoTemplate.updateFirst(query, update, Paciente.class).getModifiedCount() > 0;
    }

    @Override
    public boolean actualizarHabitacion(String idPaciente, int habitacion) {
        Query query = new Query(Criteria.where("_id").is(idPaciente).and("activo").is(true));
        Update update = new Update().set("habitacion", habitacion);

        return mongoTemplate.updateFirst(query, update, Paciente.class).getModifiedCount() > 0;
    }

    @Override
    public boolean darDeBaja(String idPaciente) {
        Query query = new Query(Criteria.where("_id").is(idPaciente).and("activo").is(true));
        Update update = new Update().set("activo", false);

        return mongoTemplate.updateFirst(query, update, Paciente.class).getModifiedCount() > 0;
    }

    @Override
    public List<Paciente> obtenerTodos() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "_id"));
        return mongoTemplate.find(query, Paciente.class);
    }
}