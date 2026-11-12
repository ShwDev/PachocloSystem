package com.pachoclosystem.pachoclosystem.repository;

import com.pachoclosystem.pachoclosystem.model.Paciente;
import com.pachoclosystem.pachoclosystem.model.RegistroClinico;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Repository;

import java.util.ArrayList;
import java.util.List;

@Repository
public class RegistroClinicoRepositoryMongo implements IRegistroClinicoRepository {

    private final MongoTemplate mongoTemplate;

    public RegistroClinicoRepositoryMongo(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void insertar(String idPaciente, RegistroClinico registro) {
        mongoTemplate.save(registro);
    }

    @Override
    public List<RegistroClinico> listarPorPaciente(String idPaciente) {
        Query query = new Query(Criteria.where("idPaciente").is(idPaciente))
                .with(Sort.by(Sort.Direction.ASC, "fecha"));
        return mongoTemplate.find(query, RegistroClinico.class);
    }

    @Override
    public List<RegistroDePaciente> listarDePacientesActivos() {
        Query queryPacientes = new Query(Criteria.where("activo").is(true));
        List<Paciente> pacientesActivos = mongoTemplate.find(queryPacientes, Paciente.class);

        List<String> idsPacientes = pacientesActivos.stream().map(Paciente::getIdPaciente).toList();

        Query queryRegistros = new Query(Criteria.where("idPaciente").in(idsPacientes))
                .with(Sort.by(Sort.Direction.ASC, "fecha"));
        List<RegistroClinico> registros = mongoTemplate.find(queryRegistros, RegistroClinico.class);

        List<RegistroDePaciente> resultado = new ArrayList<>();
        for (RegistroClinico r : registros) {
            pacientesActivos.stream()
                    .filter(p -> p.getIdPaciente().equals(r.getIdPaciente()))
                    .findFirst()
                    .ifPresent(p -> resultado.add(new RegistroDePaciente(p.getIdPaciente(), p.getNombre(), r)));
        }

        return resultado;
    }
}