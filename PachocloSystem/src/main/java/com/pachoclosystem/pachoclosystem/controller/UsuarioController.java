package com.pachoclosystem.pachoclosystem.controller;

import com.pachoclosystem.pachoclosystem.dto.CambiarRolRequest;
import com.pachoclosystem.pachoclosystem.dto.ErrorResponse;
import com.pachoclosystem.pachoclosystem.dto.PasswordRequest;
import com.pachoclosystem.pachoclosystem.dto.UsuarioRequest;
import com.pachoclosystem.pachoclosystem.dto.UsuarioResponse;
import com.pachoclosystem.pachoclosystem.model.Rol;
import com.pachoclosystem.pachoclosystem.model.Usuario;
import com.pachoclosystem.pachoclosystem.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Gestión de usuarios, solo para ADMIN (regla en {@code SecurityConfig}).
 * Las respuestas nunca incluyen el hash de la contraseña. El alta y el
 * restablecimiento de contraseña dejan al usuario con el cambio obligatorio.
 */
@RestController
@RequestMapping("/api/usuarios")
@Tag(name = "Usuarios", description = """
        Usuarios de acceso, solo para ADMIN. Las respuestas nunca incluyen contraseñas ni el
        hash. El alta y el restablecimiento dejan al usuario con el cambio obligatorio.
        """)
public class UsuarioController {

    private final UsuarioService servicio;

    public UsuarioController(UsuarioService servicio) {
        this.servicio = servicio;
    }

    @Operation(summary = "Crear un usuario",
            description = """
                    Solo ADMIN. El username debe tener 3 a 30 caracteres (minúsculas, dígitos,
                    punto, guion bajo o guion) y ser único. La contraseña: al menos 10 caracteres,
                    hasta 72 bytes en UTF-8 y distinta del username.

                    `rol` es `ADMIN`, `DOCTOR` o `ENFERMERO`. DOCTOR y ENFERMERO exigen
                    `idTrabajador` de un trabajador existente del tipo correcto; ADMIN lo omite.

                    El usuario queda con `debeCambiarPassword=true`.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Usuario creado."),
            @ApiResponse(responseCode = "400", description = "Datos inválidos (username, contraseña, "
                    + "rol o vínculo con el trabajador).",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMIN.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Ya existe un usuario con ese username.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping
    public ResponseEntity<UsuarioResponse> crear(@RequestBody UsuarioRequest request) {
        Usuario usuario = servicio.crearUsuario(request.username(), request.password(),
                rolDe(request.rol()), request.idTrabajador(), true);
        return ResponseEntity.created(URI.create("/api/usuarios/" + usuario.getIdUsuario()))
                .body(respuestaDe(usuario));
    }

    /** Activos e inactivos; {@code q} filtra por ID, username o trabajador vinculado. */
    @Operation(summary = "Listar usuarios",
            description = "Solo ADMIN. Devuelve activos e inactivos. `q` filtra por ID, username o "
                    + "nombre del trabajador vinculado.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuarios (puede estar vacío).",
                    content = @Content(array = @ArraySchema(
                            schema = @Schema(implementation = UsuarioResponse.class)))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMIN.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public List<UsuarioResponse> listar(
            @Parameter(description = "Texto a buscar en el ID, el username o el trabajador.",
                    example = "ana")
            @RequestParam(required = false) String q) {
        List<Usuario> usuarios = servicio.listar(q);
        // Los nombres se resuelven de una vez para todo el listado (sin N+1).
        Map<String, String> nombres = servicio.nombresDeTrabajadores(usuarios);
        return usuarios.stream()
                .map(usuario -> UsuarioResponse.from(usuario, nombres.get(usuario.getIdUsuario())))
                .toList();
    }

    @Operation(summary = "Obtener un usuario", description = "Solo ADMIN. Busca por su ID (`USR-0001`).")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario encontrado."),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMIN.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese usuario.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{id}")
    public UsuarioResponse obtener(
            @Parameter(description = "ID del usuario.", example = "USR-0001")
            @PathVariable String id) {
        return respuestaDe(servicio.obtener(id));
    }

    @Operation(summary = "Cambiar el rol de un usuario",
            description = """
                    Solo ADMIN. Un DOCTOR o ENFERMERO debe seguir vinculado a un trabajador del tipo
                    correcto. No se puede cambiar el rol del propio usuario ni quitar el rol ADMIN al
                    último administrador activo.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Rol actualizado."),
            @ApiResponse(responseCode = "400", description = "Rol o trabajador inválidos.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMIN.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese usuario.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Cambio del propio rol o último admin.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/rol")
    public UsuarioResponse cambiarRol(
            @Parameter(description = "ID del usuario.", example = "USR-0002")
            @PathVariable String id,
            @RequestBody CambiarRolRequest request,
            Authentication autenticacion) {
        return respuestaDe(servicio.cambiarRol(id, rolDe(request.rol()), request.idTrabajador(),
                autenticacion.getName()));
    }

    @Operation(summary = "Desactivar un usuario",
            description = "Solo ADMIN. No se puede desactivar el propio usuario ni al último "
                    + "administrador activo. Sus tokens dejan de ser válidos de inmediato.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario desactivado."),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMIN.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese usuario.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "409", description = "Es el propio usuario o el último admin activo.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/desactivar")
    public UsuarioResponse desactivar(
            @Parameter(description = "ID del usuario.", example = "USR-0002")
            @PathVariable String id,
            Authentication autenticacion) {
        return respuestaDe(servicio.desactivar(id, autenticacion.getName()));
    }

    @Operation(summary = "Activar un usuario",
            description = "Solo ADMIN. Falla si su trabajador vinculado ya no existe o no coincide "
                    + "con el rol del usuario.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario activado."),
            @ApiResponse(responseCode = "400", description = "No se puede activar: falta el trabajador "
                    + "vinculado o su rol no coincide.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMIN.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese usuario.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/activar")
    public UsuarioResponse activar(
            @Parameter(description = "ID del usuario.", example = "USR-0002")
            @PathVariable String id) {
        return respuestaDe(servicio.activar(id));
    }

    @Operation(summary = "Restablecer la contraseña",
            description = """
                    Solo ADMIN. Asigna una contraseña nueva que cumpla la política y deja al usuario
                    con `debeCambiarPassword=true`; sus tokens anteriores quedan revocados.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contraseña restablecida, sin cuerpo."),
            @ApiResponse(responseCode = "400", description = "La contraseña no cumple la política.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "El rol no es ADMIN.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "No existe ese usuario.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PatchMapping("/{id}/password")
    public ResponseEntity<Void> restablecerPassword(
            @Parameter(description = "ID del usuario.", example = "USR-0002")
            @PathVariable String id,
            @RequestBody PasswordRequest request) {
        servicio.restablecerPassword(id, request.password());
        return ResponseEntity.noContent().build();
    }

    /**
     * Respuesta de un usuario con el nombre completo de su trabajador vinculado
     * (una lectura; {@code null} si no tiene o si su trabajador ya no existe).
     */
    private UsuarioResponse respuestaDe(Usuario usuario) {
        return UsuarioResponse.from(usuario, servicio.nombreDelTrabajador(usuario));
    }

    /** Texto del rol a {@link Rol}; {@code null} si falta o no es válido (lo informa el servicio). */
    private static Rol rolDe(String texto) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Rol.valueOf(texto.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException desconocido) {
            return null;
        }
    }
}
