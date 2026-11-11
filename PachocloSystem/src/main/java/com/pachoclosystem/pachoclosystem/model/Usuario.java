package com.pachoclosystem.pachoclosystem.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Usuario de acceso a la API.
 *
 * <p>Guarda <strong>únicamente</strong> el hash BCrypt de la contraseña; la
 * contraseña en claro nunca se almacena ni se muestra. Ni {@code toString()}
 * ni la serialización JSON incluyen el hash.</p>
 *
 * <p>El hash, la versión de token y el cambio pendiente viven juntos en un
 * {@link Credenciales} inmutable que se sustituye entero: el login lo lee una
 * sola vez, comprueba la contraseña contra ese hash y pone esa versión en el
 * token, así un cambio de contraseña simultáneo nunca produce un token con la
 * versión nueva validado con la contraseña anterior. Cambiar la contraseña y
 * desactivar incrementan la versión (revocan los tokens emitidos antes);
 * {@link #reactivar()} no la restaura.</p>
 *
 * <p>El rol y el trabajador vinculado solo cambian con
 * {@link #cambiarRol(Rol, String)}, que valida su coherencia; no hay setters
 * genéricos.</p>
 */
@Document(collection = "usuarios")
public class Usuario {

    public static final Pattern PATRON_USERNAME = Pattern.compile("^[a-z0-9._-]{3,30}$");

    @Id
    private final String idUsuario;

    @Indexed(unique = true)
    private final String username;

    private volatile Credenciales credenciales;
    private volatile Rol rol;

    @Indexed(unique = true, sparse = true)
    private volatile String idTrabajador;

    private volatile boolean activo;

    public Usuario(String idUsuario, String username, String passwordHash, Rol rol, String idTrabajador) {
        this(idUsuario, username, passwordHash, rol, idTrabajador, false);
    }

    public Usuario(String idUsuario, String username, String passwordHash, Rol rol, String idTrabajador,
                   boolean debeCambiarPassword) {
        this.idUsuario = idUsuario;
        this.username = normalizarUsername(username);
        this.credenciales = new Credenciales(passwordHash, 0, debeCambiarPassword);
        this.rol = rol;
        this.idTrabajador = idTrabajador;
        this.activo = true;
    }

    /**
     * Reconstruye un usuario ya guardado, con su estado completo. Es el creador
     * que usa Spring Data al leer el documento de MongoDB: sus parámetros
     * coinciden con las propiedades persistidas ({@code idUsuario, username,
     * credenciales, rol, idTrabajador, activo}).
     */
    @PersistenceCreator
    public static Usuario restaurar(String idUsuario, String username, Credenciales credenciales, Rol rol,
                                    String idTrabajador, boolean activo) {
        Usuario usuario = new Usuario(idUsuario, username, credenciales.hash(), rol, idTrabajador);
        usuario.credenciales = credenciales;
        usuario.activo = activo;
        return usuario;
    }

    public static String normalizarUsername(String username) {
        return username == null ? null : username.trim().toLowerCase(Locale.ROOT);
    }

    public String getIdUsuario() {
        return idUsuario;
    }

    public String getUsername() {
        return username;
    }

    @JsonIgnore
    public String getPasswordHash() {
        return credenciales.hash();
    }

    @JsonIgnore
    public Credenciales getCredenciales() {
        return credenciales;
    }

    public Rol getRol() {
        return rol;
    }

    public String getIdTrabajador() {
        return idTrabajador;
    }

    public boolean isActivo() {
        return activo;
    }

    public boolean isDebeCambiarPassword() {
        return credenciales.debeCambiarPassword();
    }

    public long getVersionToken() {
        return credenciales.version();
    }

    public synchronized void desactivar() {
        if (activo) {
            activo = false;
            credenciales = credenciales.conNuevaVersion();
        }
    }

    public synchronized void reactivar() {
        activo = true;
    }

    public synchronized void cambiarRol(Rol nuevoRol, String idTrabajador) {
        if (nuevoRol == null) {
            throw new IllegalArgumentException("El rol es obligatorio.");
        }
        String trabajador = (idTrabajador == null || idTrabajador.isBlank()) ? null : idTrabajador.trim();
        if (nuevoRol == Rol.ADMIN && trabajador != null) {
            throw new IllegalArgumentException(
                    "El usuario administrador no puede estar vinculado a un trabajador.");
        }
        if (nuevoRol != Rol.ADMIN && trabajador == null) {
            throw new IllegalArgumentException(
                    "El usuario con rol " + nuevoRol + " debe estar vinculado a un trabajador.");
        }
        this.rol = nuevoRol;
        this.idTrabajador = trabajador;
    }

    public synchronized void cambiarPassword(String nuevoHash, boolean exigirCambio) {
        if (nuevoHash == null || nuevoHash.isBlank()) {
            throw new IllegalArgumentException("El hash de la contraseña es obligatorio.");
        }
        credenciales = new Credenciales(nuevoHash, credenciales.version() + 1, exigirCambio);
    }

    public record Credenciales(String hash, long version, boolean debeCambiarPassword) {
        Credenciales conNuevaVersion() {
            return new Credenciales(hash, version + 1, debeCambiarPassword);
        }

        @Override
        public String toString() {
            return "Credenciales{version=" + version + ", debeCambiarPassword=" + debeCambiarPassword + "}";
        }
    }

    @Override
    public String toString() {
        Credenciales actuales = credenciales;
        return "Usuario{idUsuario='" + idUsuario + "', username='" + username + "', rol=" + rol
                + ", idTrabajador=" + (idTrabajador == null ? "null" : "'" + idTrabajador + "'")
                + ", activo=" + activo + ", debeCambiarPassword=" + actuales.debeCambiarPassword()
                + ", versionToken=" + actuales.version() + "}";
    }

    @Override
    public boolean equals(Object otro) {
        if (this == otro) {
            return true;
        }
        if (!(otro instanceof Usuario usuario)) {
            return false;
        }
        return Objects.equals(idUsuario, usuario.idUsuario);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(idUsuario);
    }
}