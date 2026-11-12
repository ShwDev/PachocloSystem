package com.pachoclosystem.pachoclosystem.repository;

import com.pachoclosystem.pachoclosystem.model.Rol;
import com.pachoclosystem.pachoclosystem.model.Usuario;

import java.util.List;

/**
 * Usuarios de acceso. La implementación de la aplicación es
 * {@link UsuarioRepositoryMongo} (MongoDB).
 *
 * <p>Cada lectura devuelve un objeto nuevo con el estado guardado: los cambios
 * (estado, contraseña, rol) se guardan con su operación propia, nunca
 * modificando el objeto leído. Las operaciones que comprueban y escriben el
 * mismo dato llevan la condición dentro de la propia escritura, así que no
 * hace falta bloqueo de fila: si el dato cambió entre medias, la escritura no
 * se aplica y quien llama lo detecta.</p>
 */
public interface IUsuarioRepository {

    /** Genera el siguiente ID con formato USR-0001 (mismo estilo que PAC-/DOC-/ENF-). */
    String generarNuevoId();

    /**
     * Inserta el usuario si su username (case-insensitive) y su trabajador
     * vinculado no están ya ocupados por otro usuario.
     *
     * @return {@code false} si el username o el trabajador ya pertenecen a otro usuario
     */
    boolean guardar(Usuario usuario);

    /**
     * Cambia el rol y el trabajador vinculado de un usuario de forma atómica.
     * No verifica que el trabajador exista ni su tipo: eso es responsabilidad
     * del servicio. Sí garantiza que un trabajador no quede vinculado a dos
     * usuarios.
     *
     * @return {@code false} si el usuario no existe o el trabajador destino ya
     *         está vinculado a otro usuario; {@code true} si el cambio se aplicó
     */
    boolean cambiarRol(String idUsuario, Rol nuevoRol, String idTrabajador);

    /**
     * Desactiva el usuario y sube su versión de token (revoca sus tokens).
     * Idempotente: si ya estaba inactivo no cambia nada.
     *
     * @return {@code true} si estaba activo y se desactivó
     */
    boolean desactivar(String idUsuario);

    /** Reactiva el usuario sin tocar su versión de token (los tokens anteriores siguen revocados). */
    void reactivar(String idUsuario);

    /** Sustituye el hash, sube la versión de token y fija si debe cambiar la contraseña. */
    void cambiarPassword(String idUsuario, String nuevoHash, boolean exigirCambio);

    /**
     * Como {@link #cambiarPassword(String, String, boolean)}, pero solo si el
     * hash guardado sigue siendo {@code hashEsperado}: si otro cambio de
     * contraseña se adelantó, no se escribe nada. Es la comprobación de la
     * contraseña actual dentro de la propia escritura.
     *
     * @return {@code false} si el hash guardado ya no era ese (o el usuario no existe)
     */
    boolean cambiarPasswordSiSigueSiendo(String idUsuario, String hashEsperado, String nuevoHash,
                                         boolean exigirCambio);

    Usuario buscarPorId(String id);

    /**
     * Como {@link #buscarPorId(String)}. Se mantiene el nombre del contrato
     * relacional: en MongoDB no hay bloqueo de fila, las escrituras que lo
     * necesitan llevan sus condiciones dentro de la propia operación.
     */
    Usuario buscarPorIdParaActualizar(String id);

    /** Búsqueda por username sin distinguir mayúsculas. */
    Usuario buscarPorUsername(String username);

    /** Devuelve el usuario vinculado a ese trabajador, o {@code null}. */
    Usuario buscarPorIdTrabajador(String idTrabajador);

    boolean existePorUsername(String username);

    /** Número de usuarios ADMIN activos. */
    long contarAdministradoresActivos();

    /**
     * Serializa, hasta el final de la transacción en curso, las operaciones que
     * pueden dejar el sistema sin administradores activos (desactivar, activar
     * y cambiar de rol), aunque haya varias instancias del servicio: escribe
     * un contador en un documento compartido, de modo que dos operaciones
     * simultáneas chocan (conflicto de escritura) y una se reintenta con los
     * datos frescos.
     */
    void bloquearAdministracion();

    /** Todos los usuarios, ordenados por ID. */
    List<Usuario> listarTodos();
}
