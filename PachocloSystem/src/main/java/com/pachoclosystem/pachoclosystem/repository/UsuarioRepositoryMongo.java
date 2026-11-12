package com.pachoclosystem.pachoclosystem.repository;

import com.pachoclosystem.pachoclosystem.model.Rol;
import com.pachoclosystem.pachoclosystem.model.Usuario;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.annotation.Id;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class UsuarioRepositoryMongo implements IUsuarioRepository {

    /** Id del documento de cerrojo que serializa las operaciones administrativas. */
    private static final String CERROJO_ADMINISTRACION = "administracion";

    private final MongoTemplate mongoTemplate;
    private final SecuenciaGeneratorService secuenciaService;

    public UsuarioRepositoryMongo(MongoTemplate mongoTemplate, SecuenciaGeneratorService secuenciaService) {
        this.mongoTemplate = mongoTemplate;
        this.secuenciaService = secuenciaService;
    }

    @Override
    public String generarNuevoId() {
        long numero = secuenciaService.obtenerSiguienteSecuencia("usuarios_id_seq");
        return String.format("USR-%04d", numero);
    }

    @Override
    public boolean guardar(Usuario usuario) {
        if (usuario == null || usuario.getIdUsuario() == null || usuario.getUsername() == null) {
            return false;
        }
        try {
            mongoTemplate.insert(usuario);
            return true;
        } catch (DuplicateKeyException ocupado) {
            return false;
        }
    }

    @Override
    public boolean cambiarRol(String idUsuario, Rol nuevoRol, String idTrabajador) {
        if (idUsuario == null || nuevoRol == null) {
            return false;
        }
        try {
            Query query = new Query(Criteria.where("_id").is(idUsuario));
            Update update = new Update()
                    .set("rol", nuevoRol)
                    .set("idTrabajador", idTrabajador);
            // Matched y no modified: guardar el mismo rol y trabajador no cambia
            // nada y no es un error.
            return mongoTemplate.updateFirst(query, update, Usuario.class).getMatchedCount() > 0;
        } catch (DuplicateKeyException trabajadorOcupado) {
            return false;
        }
    }

    @Override
    public boolean desactivar(String idUsuario) {
        Query query = new Query(Criteria.where("_id").is(idUsuario).and("activo").is(true));
        Update update = new Update()
                .set("activo", false)
                .inc("credenciales.version", 1);
        return mongoTemplate.updateFirst(query, update, Usuario.class).getModifiedCount() > 0;
    }

    @Override
    public void reactivar(String idUsuario) {
        Query query = new Query(Criteria.where("_id").is(idUsuario));
        Update update = new Update().set("activo", true);
        mongoTemplate.updateFirst(query, update, Usuario.class);
    }

    @Override
    public void cambiarPassword(String idUsuario, String nuevoHash, boolean exigirCambio) {
        if (nuevoHash == null || nuevoHash.isBlank()) {
            throw new IllegalArgumentException("El hash de la contraseña es obligatorio.");
        }
        Query query = new Query(Criteria.where("_id").is(idUsuario));
        Update update = new Update()
                .set("credenciales.hash", nuevoHash)
                .set("credenciales.debeCambiarPassword", exigirCambio)
                .inc("credenciales.version", 1);
        mongoTemplate.updateFirst(query, update, Usuario.class);
    }

    @Override
    public boolean cambiarPasswordSiSigueSiendo(String idUsuario, String hashEsperado, String nuevoHash,
                                                boolean exigirCambio) {
        if (idUsuario == null || nuevoHash == null || nuevoHash.isBlank()) {
            throw new IllegalArgumentException("El hash de la contraseña es obligatorio.");
        }
        // El hash esperado va en la condición: si otro cambio de contraseña se
        // adelantó, la escritura no se aplica (equivale al bloqueo de fila).
        Query query = new Query(Criteria.where("_id").is(idUsuario)
                .and("credenciales.hash").is(hashEsperado));
        Update update = new Update()
                .set("credenciales.hash", nuevoHash)
                .set("credenciales.debeCambiarPassword", exigirCambio)
                .inc("credenciales.version", 1);
        return mongoTemplate.updateFirst(query, update, Usuario.class).getMatchedCount() > 0;
    }

    @Override
    public Usuario buscarPorId(String id) {
        if (id == null) {
            return null;
        }
        return mongoTemplate.findById(id, Usuario.class);
    }

    @Override
    public Usuario buscarPorIdParaActualizar(String id) {
        return buscarPorId(id);
    }

    @Override
    public Usuario buscarPorUsername(String username) {
        String normalizado = Usuario.normalizarUsername(username);
        if (normalizado == null) {
            return null;
        }
        Query query = new Query(Criteria.where("username").is(normalizado));
        return mongoTemplate.findOne(query, Usuario.class);
    }

    @Override
    public Usuario buscarPorIdTrabajador(String idTrabajador) {
        if (idTrabajador == null) {
            return null;
        }
        Query query = new Query(Criteria.where("idTrabajador").is(idTrabajador));
        return mongoTemplate.findOne(query, Usuario.class);
    }

    @Override
    public boolean existePorUsername(String username) {
        return buscarPorUsername(username) != null;
    }

    @Override
    public long contarAdministradoresActivos() {
        Query query = new Query(Criteria.where("rol").is(Rol.ADMIN).and("activo").is(true));
        return mongoTemplate.count(query, Usuario.class);
    }

    @Override
    public void bloquearAdministracion() {
        // Cerrojo compartido: incrementar el mismo documento dentro de la
        // transacción hace que dos operaciones administrativas simultáneas
        // choquen (conflicto de escritura) y una se reintente con datos frescos.
        Query query = new Query(Criteria.where("_id").is(CERROJO_ADMINISTRACION));
        Update update = new Update().inc("contador", 1);
        mongoTemplate.upsert(query, update, CerrojoDocument.class);
    }

    @Override
    public List<Usuario> listarTodos() {
        Query query = new Query().with(Sort.by(Sort.Direction.ASC, "_id"));
        return mongoTemplate.find(query, Usuario.class);
    }

    /** Documento único que serializa las operaciones administrativas. */
    @Document(collection = "cerrojos")
    static class CerrojoDocument {
        @Id
        private String id;
        private long contador;

        public CerrojoDocument() {}
    }
}