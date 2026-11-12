package com.pachoclosystem.medicamentos.repository;

import com.pachoclosystem.medicamentos.model.DatosMedicamento;
import com.pachoclosystem.medicamentos.model.Medicamento;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Medicamentos en la colección {@code medicamentos}.
 *
 * <ul>
 *   <li>La unicidad de nombre + concentración + presentación + lote la decide el
 *       índice único sobre {@code claveUnica}, que guarda
 *       {@link DatosMedicamento#claveUnica()}: la normalización es la de Java.</li>
 *   <li>El id sale de la secuencia {@code medicamentos_id_seq} con el formato
 *       {@code MED-0001}. Un alta que falla también consume su número.</li>
 *   <li>Las entradas y salidas de stock no leen el medicamento para luego
 *       escribirlo: cada una es un único {@code findAndModify} atómico con sus
 *       condiciones dentro de la consulta (comprobar y escribir en el mismo
 *       paso), así que dos salidas simultáneas nunca dejan el stock en negativo
 *       ni se pierde una actualización, aunque haya varias instancias del
 *       servicio. Por eso no hace falta transacción ni bloqueo.</li>
 * </ul>
 */
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
        if (medicamento == null || medicamento.getIdMedicamento() == null || nuevosDatos == null) {
            return ResultadoActualizacion.NO_EXISTE;
        }

        try {
            Query query = new Query(Criteria.where("_id").is(medicamento.getIdMedicamento()));
            Update update = new Update()
                    .set("claveUnica", nuevosDatos.claveUnica())
                    .set("datos.nombre", nuevosDatos.nombre())
                    .set("datos.principioActivo", nuevosDatos.principioActivo())
                    .set("datos.presentacion", nuevosDatos.presentacion())
                    .set("datos.concentracion", nuevosDatos.concentracion())
                    .set("datos.laboratorio", nuevosDatos.laboratorio())
                    .set("datos.lote", nuevosDatos.lote())
                    .set("datos.stockMinimo", nuevosDatos.stockMinimo())
                    .set("datos.fechaVencimiento", nuevosDatos.fechaVencimiento())
                    .set("datos.ubicacion", nuevosDatos.ubicacion());

            // Matched y no modified: MongoDB cuenta 0 modificados cuando los
            // valores nuevos son iguales a los guardados, y eso no es un error.
            boolean encontrado = mongoTemplate.updateFirst(query, update, Medicamento.class)
                    .getMatchedCount() > 0;
            if (!encontrado) {
                return ResultadoActualizacion.NO_EXISTE;
            }
            medicamento.setDatos(nuevosDatos);
            return ResultadoActualizacion.ACTUALIZADO;
        } catch (DuplicateKeyException duplicado) {
            // Otro medicamento ya tiene la nueva clave de unicidad: nada cambia.
            return ResultadoActualizacion.DUPLICADO;
        }
    }

    @Override
    public Medicamento sumarStock(String id, int cantidad) {
        if (id == null) {
            return null;
        }
        // El tope en long evita el desborde de Java si la cantidad fuera
        // negativa; MongoDB compara números de cualquier anchura.
        Query query = new Query(Criteria.where("_id").is(id)
                .and("cantidadStock").lte((long) Integer.MAX_VALUE - cantidad));
        Update update = new Update().inc("cantidadStock", cantidad);
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Medicamento.class);
    }

    @Override
    public Medicamento descontarStock(String id, int cantidad, LocalDate hoy) {
        if (id == null) {
            return null;
        }
        Query query = new Query(Criteria.where("_id").is(id)
                .and("cantidadStock").gte(cantidad)
                .and("datos.fechaVencimiento").gte(hoy));
        Update update = new Update().inc("cantidadStock", -cantidad);
        return mongoTemplate.findAndModify(query, update,
                FindAndModifyOptions.options().returnNew(true), Medicamento.class);
    }

    @Override
    public Medicamento buscarPorId(String id) {
        if (id == null) {
            return null;
        }
        return mongoTemplate.findById(id, Medicamento.class);
    }

    /** Ordenados por id como {@code String.compareTo} (los ids son ASCII). */
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
