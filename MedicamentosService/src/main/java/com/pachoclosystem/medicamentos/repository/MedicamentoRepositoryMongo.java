package com.pachoclosystem.medicamentos.repository;

import com.pachoclosystem.medicamentos.model.DatosMedicamento;
import com.pachoclosystem.medicamentos.model.Medicamento;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class MedicamentoRepositoryMongo implements IMedicamentoRepository {

    private final MongoTemplate mongoTemplate;
    private final SecuenciaGeneratorService secuenciaService;

    public MedicamentoRepositoryMongo(MongoTemplate mongoTemplate, SecuenciaGeneratorService secuenciaService) {
        this.mongoTemplate = mongoTemplate;
        this.secuenciaService = secuenciaService;
    }

    @Override
    public String generarNuevoId() {
        long numero = secuenciaService.obtenerSiguienteSecuencia("medicamentos_id_seq");
        return String.format("MED-%04d", numero);
    }

    @Override
    public boolean guardar(Medicamento medicamento) {
        if (medicamento == null || medicamento.getIdMedicamento() == null) {
            return false;
        }
        try {
            mongoTemplate.insert(medicamento);
            return true;
        } catch (DuplicateKeyException duplicado) {
            return false;
        }
    }

    @Override
    public ResultadoActualizacion actualizarDatos(Medicamento medicamento, DatosMedicamento nuevosDatos) {
        if (medicamento == null || medicamento.getIdMedicamento() == null) {
            return ResultadoActualizacion.NO_EXISTE;
        }

        try {
            Query query = new Query(Criteria.where("_id").is(medicamento.getIdMedicamento()));
            Update update = new Update()
                    .set("datos.nombre", nuevosDatos.nombre())
                    .set("datos.principioActivo", nuevosDatos.principioActivo())
                    .set("datos.presentacion", nuevosDatos.presentacion())
                    .set("datos.concentracion", nuevosDatos.concentracion())
                    .set("datos.laboratorio", nuevosDatos.laboratorio())
                    .set("datos.lote", nuevosDatos.lote())
                    .set("datos.stockMinimo", nuevosDatos.stockMinimo())
                    .set("datos.fechaVencimiento", nuevosDatos.fechaVencimiento())
                    .set("datos.ubicacion", nuevosDatos.ubicacion());

            long modificados = mongoTemplate.updateFirst(query, update, Medicamento.class).getModifiedCount();
            if (modificados == 0) {
                return ResultadoActualizacion.NO_EXISTE;
            }
            medicamento.setDatos(nuevosDatos);
            return ResultadoActualizacion.ACTUALIZADO;
        } catch (DuplicateKeyException duplicado) {
            return ResultadoActualizacion.DUPLICADO;
        }
    }

    @Override
    public void actualizarStock(String id, int cantidadStock) {
        Query query = new Query(Criteria.where("_id").is(id));
        Update update = new Update().set("cantidadStock", cantidadStock);
        mongoTemplate.updateFirst(query, update, Medicamento.class);
    }

    @Override
    public Medicamento buscarPorId(String id) {
        if (id == null) {
            return null;
        }
        return mongoTemplate.findById(id, Medicamento.class);
    }

    @Override
    public Medicamento buscarPorIdParaActualizar(String id) {
        return buscarPorId(id);
    }

    @Override
    public List<Medicamento> listarTodos() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "_id"));
        return mongoTemplate.find(query, Medicamento.class);
    }

    @Override
    public boolean eliminar(String id) {
        if (id == null) {
            return false;
        }
        Query query = new Query(Criteria.where("_id").is(id));
        return mongoTemplate.remove(query, Medicamento.class).getDeletedCount() > 0;
    }
}