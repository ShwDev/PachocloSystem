package com.pachoclosystem.pachoclosystem.controller;

import com.pachoclosystem.pachoclosystem.dto.CambiarPasswordRequest;
import com.pachoclosystem.pachoclosystem.dto.ErrorResponse;
import com.pachoclosystem.pachoclosystem.dto.LoginRequest;
import com.pachoclosystem.pachoclosystem.dto.LoginResponse;
import com.pachoclosystem.pachoclosystem.dto.UsuarioResponse;
import com.pachoclosystem.pachoclosystem.model.Usuario;
import com.pachoclosystem.pachoclosystem.security.AuthService;
import com.pachoclosystem.pachoclosystem.security.ResolutorIpCliente;
import com.pachoclosystem.pachoclosystem.service.UsuarioService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Autenticación: {@code POST /api/auth/login} (público) emite el token JWT,
 * {@code GET /api/auth/me} (autenticado) devuelve el usuario actual releyendo
 * el repositorio (nunca los claims del token) y {@code POST /api/auth/password}
 * (autenticado) cambia la contraseña del usuario, revocando de inmediato los
 * tokens emitidos antes del cambio.
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticación", description = """
        Login, usuario actual y cambio de contraseña propia.
        """)
public class AuthController {

    private final AuthService authService;
    private final UsuarioService usuarioService;
    private final ResolutorIpCliente resolutorIp;

    public AuthController(AuthService authService, UsuarioService usuarioService,
                          ResolutorIpCliente resolutorIp) {
        this.authService = authService;
        this.usuarioService = usuarioService;
        this.resolutorIp = resolutorIp;
    }

    @Operation(summary = "Iniciar sesión",
            description = """
                    Comprueba las credenciales y devuelve un token JWT válido durante 30 minutos.

                    Los intentos fallidos se limitan por usuario y por IP; al superar el límite
                    responde 429 con la cabecera `Retry-After` (segundos de bloqueo). Un usuario
                    desactivado no puede iniciar sesión.
                    """)
    @SecurityRequirements
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Credenciales correctas; incluye el token."),
            @ApiResponse(responseCode = "400", description = "Faltan el username o la contraseña.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Credenciales inválidas.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "429", description = "Demasiados intentos fallidos; "
                    + "vea la cabecera `Retry-After`.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/login")
    public LoginResponse login(@Valid @RequestBody LoginRequest solicitud, HttpServletRequest peticionHttp) {
        // La IP es la del socket; X-Forwarded-For solo cuenta si la petición
        // llega de un proxy configurado como de confianza (ResolutorIpCliente).
        // Fiarse siempre de la cabecera permitiría a un cliente falsificar su
        // IP para esquivar (o provocar) el bloqueo de terceros.
        return authService.iniciarSesion(solicitud.username(), solicitud.password(),
                resolutorIp.ipDe(peticionHttp));
    }

    @Operation(summary = "Usuario autenticado",
            description = "Devuelve el usuario del token, releído de la base de datos: refleja "
                    + "cambios de rol, bajas y el estado de la contraseña.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Usuario autenticado."),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "El usuario del token ya no existe.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/me")
    public UsuarioResponse me(Authentication autenticacion) {
        // El nombre de la autenticación es el username validado y activo que
        // fija JwtUsuarioAuthenticationConverter al releer el repositorio.
        Usuario usuario = usuarioService.buscarPorUsername(autenticacion.getName());
        return UsuarioResponse.from(usuario, usuarioService.nombreDelTrabajador(usuario));
    }

    /**
     * Cambio de contraseña del propio usuario. Tras el cambio, el token que
     * acaba de usarse queda revocado (la versión del token se incrementa): el
     * cliente debe iniciar sesión de nuevo con la nueva contraseña.
     */
    @Operation(summary = "Cambiar la contraseña propia",
            description = """
                    Cambia la contraseña del usuario autenticado. Exige la actual y aplica la
                    política: al menos 10 caracteres, hasta 72 bytes en UTF-8 y distinta del
                    username.

                    Tras el cambio, **todos** los tokens emitidos antes quedan revocados: hay que
                    iniciar sesión de nuevo con la nueva contraseña.
                    """)
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Contraseña cambiada, sin cuerpo."),
            @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta o nueva "
                    + "contraseña que no cumple la política.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "401", description = "Falta el token o no es válido.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "503", description = "La base de datos no está disponible.",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/password")
    public ResponseEntity<Void> cambiarPassword(@Valid @RequestBody CambiarPasswordRequest solicitud,
                                                Authentication autenticacion) {
        Usuario usuario = usuarioService.buscarPorUsername(autenticacion.getName());
        usuarioService.cambiarPasswordPropia(usuario.getIdUsuario(),
                solicitud.passwordActual(), solicitud.passwordNueva());
        return ResponseEntity.noContent().build();
    }
}
