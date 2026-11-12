package com.pachoclosystem.pachoclosystem.repository;

import com.pachoclosystem.pachoclosystem.model.Rol;
import com.pachoclosystem.pachoclosystem.model.Usuario;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Doble en memoria de {@link IUsuarioRepository} para los tests unitarios, sin
 * base de datos ({@link ConcurrentHashMap}). Devuelve siempre el mismo objeto
 * por usuario; la concurrencia real se prueba contra MongoDB.
 *
 * <p>Ningún método está {@code synchronized}: la unicidad de username y de
 * trabajador vinculado se resuelve con {@code putIfAbsent} sobre índices
 * auxiliares, operación atómica en las colecciones concurrentes. No se usa el
 * patrón leer-comprobar-escribir.</p>
 */
public class UsuarioRepositoryEnMemoria implements IUsuarioRepository {

    private static final String PREFIJO_ID = "USR";

    /** ID de usuario -> usuario. */
    private final Map<String, Usuario> usuarios = new ConcurrentHashMap<>();
    /** Username normalizado (minúsculas) -> ID de usuario. */
    private final Map<String, String> indicePorUsername = new ConcurrentHashMap<>();
    /** ID de trabajador vinculado -> ID de usuario. */
    private final Map<String, String> indicePorIdTrabajador = new ConcurrentHashMap<>();

    private final AtomicInteger contadorId = new AtomicInteger(1);

    @Override
    public String generarNuevoId() {
        return String.format("%s-%04d", PREFIJO_ID, contadorId.getAndIncrement());
    }

    @Override
    public boolean guardar(Usuario usuario) {
        if (usuario == null) {
            return false;
        }
        String id = usuario.getIdUsuario();
        String username = usuario.getUsername();

        // Unicidad de username: putIfAbsent es atómico, gana un solo hilo.
        String idQueYaTieneElUsername = indicePorUsername.putIfAbsent(username, id);
        boolean usernameRecienReservado = idQueYaTieneElUsername == null;
        if (!usernameRecienReservado && !idQueYaTieneElUsername.equals(id)) {
            return false;
        }

        // Unicidad del trabajador vinculado (si lo hay).
        if (usuario.getIdTrabajador() != null) {
            String idQueYaVinculaAlTrabajador =
                    indicePorIdTrabajador.putIfAbsent(usuario.getIdTrabajador(), id);
            if (idQueYaVinculaAlTrabajador != null && !idQueYaVinculaAlTrabajador.equals(id)) {
                // Se deshace solo la reserva recién hecha; si el username ya estaba
                // reservado por este mismo usuario, se conserva.
                if (usernameRecienReservado) {
                    indicePorUsername.remove(username, id);
                }
                return false;
            }
        }

        // Si se reemplaza a un usuario ya existente cuyo trabajador era otro, se
        // libera el índice del trabajador antiguo para no dejar apuntadores
        // obsoletos. (El cambio de rol programático usa cambiarRol, que además
        // está serializado por usuario; esto cubre el reemplazo directo.)
        Usuario anterior = usuarios.put(id, usuario);
        if (anterior != null && anterior.getIdTrabajador() != null
                && !anterior.getIdTrabajador().equals(usuario.getIdTrabajador())) {
            indicePorIdTrabajador.remove(anterior.getIdTrabajador(), id);
        }
        return true;
    }

    /**
     * Cambio de rol atómico. El {@code compute} sobre el mapa de usuarios
     * serializa los cambios de un mismo usuario (no hay lectura-comprobar-
     * escritura sobre sus campos mutables), y el {@code putIfAbsent} sobre el
     * índice de trabajadores arbitra quién reserva cada trabajador. La reserva
     * del trabajador nuevo se hace antes de liberar el antiguo, de modo que
     * nunca quedan dos usuarios apuntando al mismo trabajador ni el índice
     * apunta a un trabajador que el usuario ya no tiene.
     */
    @Override
    public boolean cambiarRol(String idUsuario, Rol nuevoRol, String idTrabajador) {
        if (idUsuario == null) {
            return false;
        }
        AtomicBoolean aplicado = new AtomicBoolean(false);
        usuarios.compute(idUsuario, (id, usuario) -> {
            if (usuario == null) {
                return null;
            }
            String trabajadorAnterior = usuario.getIdTrabajador();
            if (Objects.equals(trabajadorAnterior, idTrabajador)) {
                usuario.cambiarRol(nuevoRol, idTrabajador);
                aplicado.set(true);
                return usuario;
            }
            if (idTrabajador != null) {
                String idQueYaVincula = indicePorIdTrabajador.putIfAbsent(idTrabajador, idUsuario);
                if (idQueYaVincula != null && !idQueYaVincula.equals(idUsuario)) {
                    return usuario;
                }
            }
            usuario.cambiarRol(nuevoRol, idTrabajador);
            if (trabajadorAnterior != null) {
                indicePorIdTrabajador.remove(trabajadorAnterior, idUsuario);
            }
            aplicado.set(true);
            return usuario;
        });
        return aplicado.get();
    }

    @Override
    public boolean desactivar(String idUsuario) {
        Usuario usuario = buscarPorId(idUsuario);
        if (usuario == null || !usuario.isActivo()) {
            return false;
        }
        usuario.desactivar();
        return true;
    }

    @Override
    public void reactivar(String idUsuario) {
        Usuario usuario = buscarPorId(idUsuario);
        if (usuario != null) {
            usuario.reactivar();
        }
    }

    @Override
    public void cambiarPassword(String idUsuario, String nuevoHash, boolean exigirCambio) {
        Usuario usuario = buscarPorId(idUsuario);
        if (usuario != null) {
            usuario.cambiarPassword(nuevoHash, exigirCambio);
        }
    }

    @Override
    public boolean cambiarPasswordSiSigueSiendo(String idUsuario, String hashEsperado, String nuevoHash,
                                                boolean exigirCambio) {
        Usuario usuario = buscarPorId(idUsuario);
        if (usuario == null || !Objects.equals(usuario.getPasswordHash(), hashEsperado)) {
            return false;
        }
        usuario.cambiarPassword(nuevoHash, exigirCambio);
        return true;
    }

    @Override
    public Usuario buscarPorId(String id) {
        return id == null ? null : usuarios.get(id);
    }

    @Override
    public Usuario buscarPorIdParaActualizar(String id) {
        return buscarPorId(id);
    }

    @Override
    public long contarAdministradoresActivos() {
        return usuarios.values().stream().filter(u -> u.getRol() == Rol.ADMIN && u.isActivo()).count();
    }

    /** Sin efecto en memoria: los tests unitarios no prueban la concurrencia. */
    @Override
    public void bloquearAdministracion() {
        // Sin efecto.
    }

    @Override
    public Usuario buscarPorUsername(String username) {
        String normalizado = Usuario.normalizarUsername(username);
        if (normalizado == null) {
            return null;
        }
        String id = indicePorUsername.get(normalizado);
        return id == null ? null : usuarios.get(id);
    }

    @Override
    public Usuario buscarPorIdTrabajador(String idTrabajador) {
        if (idTrabajador == null) {
            return null;
        }
        String id = indicePorIdTrabajador.get(idTrabajador);
        return id == null ? null : usuarios.get(id);
    }

    @Override
    public boolean existePorUsername(String username) {
        String normalizado = Usuario.normalizarUsername(username);
        return normalizado != null && indicePorUsername.containsKey(normalizado);
    }

    @Override
    public List<Usuario> listarTodos() {
        List<Usuario> listados = new ArrayList<>(usuarios.values());
        listados.sort((a, b) -> a.getIdUsuario().compareTo(b.getIdUsuario()));
        return listados;
    }
}
