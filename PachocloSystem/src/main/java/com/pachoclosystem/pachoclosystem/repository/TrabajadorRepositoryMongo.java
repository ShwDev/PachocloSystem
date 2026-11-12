package com.pachoclosystem.pachoclosystem.repository;

import com.pachoclosystem.pachoclosystem.model.Doctor;
import com.pachoclosystem.pachoclosystem.model.Enfermero;
import com.pachoclosystem.pachoclosystem.model.TrabajadorHospital;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class TrabajadorRepositoryMongo implements ITrabajadoresRepository {

    private final MongoTemplate mongoTemplate;
    private final SecuenciaGeneratorService secuenciaService;

    public TrabajadorRepositoryMongo(MongoTemplate mongoTemplate, SecuenciaGeneratorService secuenciaService) {
        this.mongoTemplate = mongoTemplate;
        this.secuenciaService = secuenciaService;
    }

    @Override
    public String generarNuevoId(String prefijo) {
        String secuencia;
        if ("DOC".equals(prefijo)) {
            secuencia = "doctores_id_seq";
        } else if ("ENF".equals(prefijo)) {
            secuencia = "enfermeros_id_seq";
        } else {
            throw new IllegalArgumentException("Prefijo de trabajador desconocido: " + prefijo);
        }

        long numero = secuenciaService.obtenerSiguienteSecuencia(secuencia);
        return String.format("%s-%04d", prefijo, numero);
    }

    @Override
    public boolean guardarTrabajador(TrabajadorHospital u) {
        if (u == null || u.getIdTrabajador() == null) {
            return false;
        }
        mongoTemplate.save(u);
        return true;
    }

    @Override
    public boolean actualizarTrabajador(TrabajadorHospital t) {
        if (t == null || t.getIdTrabajador() == null) {
            return false;
        }

        Query query = new Query(Criteria.where("_id").is(t.getIdTrabajador()));
        Update update = new Update().set("nombreCompleto", t.getNombreCompleto());

        if (t instanceof Doctor d) {
            update.set("especialidad", d.getEspecialidad());
        } else if (t instanceof Enfermero e) {
            update.set("nivelExperiencia", e.getNivelExperiencia());
        }

        return mongoTemplate.updateFirst(query, update, TrabajadorHospital.class).getMatchedCount() > 0;
    }

    @Override
    public TrabajadorHospital buscarPorId(String id) {
        if (id == null) {
            return null;
        }
        return mongoTemplate.findById(id, TrabajadorHospital.class);
    }

    @Override
    public TrabajadorHospital buscarPorIdParaActualizar(String id) {
        return buscarPorId(id);
    }

    @Override
    public boolean eliminarTrabajador(String id) {
        if (id == null) {
            return false;
        }
        Query query = new Query(Criteria.where("_id").is(id));
        return mongoTemplate.remove(query, TrabajadorHospital.class).getDeletedCount() > 0;
    }

    @Override
    public List<TrabajadorHospital> obtenerTodos() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "_id"));
        return mongoTemplate.find(query, TrabajadorHospital.class);
    }
}