package com.pachoclosystem.pachoclosystem.exception;

import com.pachoclosystem.pachoclosystem.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.ErrorResponseException;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.List;

/**
 * Manejador global de errores: toda la respuesta de error de la API es un
 * {@link ErrorResponse} uniforme (status, error y lista de mensajes), sin
 * trazas de pila, sin mensajes internos y sin nombres de clases Java.
 *
 * <p>El detalle real de cada fallo solo se registra en el log del servidor.</p>
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final String MENSAJE_RECURSO_NO_ENCONTRADO =
            "No se encontró el recurso solicitado.";
    private static final String MENSAJE_METODO_NO_PERMITIDO =
            "El método de la petición no está permitido en este recurso.";
    private static final String MENSAJE_CUERPO_INVALIDO =
            "El cuerpo de la petición es inválido o tiene valores no reconocidos.";
    private static final String MENSAJE_ARGUMENTO_INVALIDO =
            "El valor de un parámetro de la petición no es válido.";
    private static final String MENSAJE_TIPO_NO_SOPORTADO =
            "El tipo de contenido de la petición no está soportado por este recurso.";
    private static final String MENSAJE_TIPO_ACEPTADO_NO_DISPONIBLE =
            "El tipo de contenido solicitado en la respuesta no está disponible.";
    private static final String MENSAJE_ERROR_INTERNO =
            "Se produjo un error interno. Vuelva a intentarlo más tarde.";
    private static final String MENSAJE_CREDENCIALES_INVALIDAS = "Credenciales inválidas.";
    private static final String MENSAJE_SERVICIO_NO_DISPONIBLE =
            "El servicio de medicamentos no está disponible. Vuelva a intentarlo más tarde.";
    private static final String MENSAJE_RESPUESTA_SERVICIO_INVALIDA =
            "El servicio de medicamentos respondió de forma inesperada.";
    private static final String MENSAJE_SALIDA_NO_CONFIRMADA =
            "No se pudo confirmar; puede reintentar sin riesgo de descontar dos veces.";
    private static final String MENSAJE_DEMASIADOS_INTENTOS =
            "Demasiados intentos fallidos. Inténtelo de nuevo más tarde.";
    private static final String MENSAJE_ACCESO_DENEGADO =
            "No tiene permisos para realizar esta operación.";

    /** 401: credenciales incorrectas en el login (usuario inexistente, contraseña mala o desactivado). */
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<ErrorResponse> credencialesInvalidas(CredencialesInvalidasException ex) {
        return respuesta(HttpStatus.UNAUTHORIZED, List.of(MENSAJE_CREDENCIALES_INVALIDAS));
    }

    /**
     * 403 de una regla de negocio (p. ej. un enfermero no crea diagnósticos):
     * mismo cuerpo uniforme que el 403 de las reglas por URL, con el motivo.
     */
    @ExceptionHandler(AccesoDenegadoException.class)
    public ResponseEntity<ErrorResponse> accesoDenegado(AccesoDenegadoException ex) {
        return respuesta(HttpStatus.FORBIDDEN, List.of(ex.getMessage()));
    }

    /**
     * 403 si una {@link AccessDeniedException} de Spring Security (o su subclase
     * {@code AuthorizationDeniedException}) llega a un controlador: sin esto
     * caería en el 500 genérico. Mensaje fijo, sin el detalle interno. Las
     * reglas de negocio usan {@link AccesoDenegadoException}.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> accesoDenegadoSeguridad(AccessDeniedException ex) {
        return respuesta(HttpStatus.FORBIDDEN, List.of(MENSAJE_ACCESO_DENEGADO));
    }

    /**
     * 429: bloqueo temporal del login por demasiados intentos fallidos (por
     * usuario o por IP). La cabecera {@code Retry-After} lleva los segundos
     * restantes del bloqueo y el cuerpo es el {@code ErrorResponse} uniforme,
     * sin revelar si el usuario existe.
     */
    @ExceptionHandler(DemasiadosIntentosException.class)
    public ResponseEntity<ErrorResponse> demasiadosIntentos(DemasiadosIntentosException ex) {
        HttpHeaders cabeceras = new HttpHeaders();
        cabeceras.set(HttpHeaders.RETRY_AFTER, Long.toString(ex.getSegundosRestantes()));
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .headers(cabeceras)
                .body(new ErrorResponse(429, HttpStatus.TOO_MANY_REQUESTS.getReasonPhrase(),
                        List.of(MENSAJE_DEMASIADOS_INTENTOS)));
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> noEncontrado(NotFoundException ex) {
        return respuesta(HttpStatus.NOT_FOUND, List.of(ex.getMessage()));
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<ErrorResponse> conflicto(ConflictoException ex) {
        return respuesta(HttpStatus.CONFLICT, List.of(ex.getMessage()));
    }

    /** 503: el servicio de medicamentos no respondió (caído o timeout). */
    @ExceptionHandler(ServicioNoDisponibleException.class)
    public ResponseEntity<ErrorResponse> servicioNoDisponible(ServicioNoDisponibleException ex) {
        LOG.warn("Servicio externo no disponible: {}", ex.getMessage());
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, List.of(MENSAJE_SERVICIO_NO_DISPONIBLE));
    }

    /**
     * 503: una salida de stock con clave de idempotencia no respondió ni al
     * reintento. Repetir la petición con la misma clave es seguro.
     */
    @ExceptionHandler(SalidaNoConfirmadaException.class)
    public ResponseEntity<ErrorResponse> salidaNoConfirmada(SalidaNoConfirmadaException ex) {
        LOG.warn("Salida de stock sin confirmar: {}", ex.getMessage());
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, List.of(MENSAJE_SALIDA_NO_CONFIRMADA));
    }

    /**
     * 503: la base de datos (MongoDB) no está disponible (sin conexión, sin
     * transacción o fallo transitorio como un conflicto de escritura). Mensaje
     * genérico; la transacción se deshizo, así que repetir la petición es seguro.
     */
    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class,
            TransientDataAccessException.class})
    public ResponseEntity<ErrorResponse> baseDeDatosNoDisponible(Exception ex) {
        LOG.warn("Base de datos no disponible: {}", ex.getMessage());
        return respuesta(HttpStatus.SERVICE_UNAVAILABLE, List.of(BaseDeDatosNoDisponible.MENSAJE));
    }

    /** 502: el servicio de medicamentos respondió algo que no se puede trasladar al cliente. */
    @ExceptionHandler(RespuestaServicioInvalidaException.class)
    public ResponseEntity<ErrorResponse> respuestaServicioInvalida(RespuestaServicioInvalidaException ex) {
        LOG.error("Respuesta inesperada de un servicio externo: {}", ex.getMessage());
        return respuesta(HttpStatus.BAD_GATEWAY, List.of(MENSAJE_RESPUESTA_SERVICIO_INVALIDA));
    }

    @ExceptionHandler(SolicitudInvalidaException.class)
    public ResponseEntity<ErrorResponse> solicitudInvalida(SolicitudInvalidaException ex) {
        return respuesta(HttpStatus.BAD_REQUEST, ex.getErrores());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> validacion(MethodArgumentNotValidException ex) {
        List<String> mensajes = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getDefaultMessage())
                .sorted()
                .toList();
        return respuesta(HttpStatus.BAD_REQUEST, mensajes);
    }

    /** 404: ruta inexistente (URL no mapeada o recurso físico). */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErrorResponse> recursoNoEncontrado(NoResourceFoundException ex) {
        return respuesta(HttpStatus.NOT_FOUND, List.of(MENSAJE_RECURSO_NO_ENCONTRADO));
    }

    /**
     * 405: método no permitido en la ruta. Conserva la cabecera {@code Allow}
     * que exige la RFC 9110.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErrorResponse> metodoNoSoportado(HttpRequestMethodNotSupportedException ex) {
        HttpHeaders cabeceras = new HttpHeaders();
        if (ex.getSupportedHttpMethods() != null) {
            cabeceras.setAllow(ex.getSupportedHttpMethods());
        }
        return ResponseEntity.status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers(cabeceras)
                .body(new ErrorResponse(405, HttpStatus.METHOD_NOT_ALLOWED.getReasonPhrase(),
                        List.of(MENSAJE_METODO_NO_PERMITIDO)));
    }

    /** 400: cuerpo JSON malformado o con valores no reconocidos. */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> cuerpoIlegible(HttpMessageNotReadableException ex) {
        LOG.warn("Cuerpo de petición no legible: {}", ex.getMessage());
        return respuesta(HttpStatus.BAD_REQUEST, List.of(MENSAJE_CUERPO_INVALIDO));
    }

    /** 400: parámetro de ruta o query con un valor del tipo incorrecto. */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ErrorResponse> argumentoDeTipoIncorrecto(MethodArgumentTypeMismatchException ex) {
        LOG.warn("Argumento '{}' con valor no válido: {}", ex.getName(), ex.getValue());
        return respuesta(HttpStatus.BAD_REQUEST, List.of(MENSAJE_ARGUMENTO_INVALIDO));
    }

    /** 415: el recurso no acepta el {@code Content-Type} enviado. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ErrorResponse> tipoNoSoportado(HttpMediaTypeNotSupportedException ex) {
        return respuesta(HttpStatus.UNSUPPORTED_MEDIA_TYPE, List.of(MENSAJE_TIPO_NO_SOPORTADO));
    }

    /** 406: no existe representación aceptable para el {@code Accept} enviado. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ErrorResponse> tipoAceptadoNoDisponible(HttpMediaTypeNotAcceptableException ex) {
        return respuesta(HttpStatus.NOT_ACCEPTABLE, List.of(MENSAJE_TIPO_ACEPTADO_NO_DISPONIBLE));
    }

    /**
     * Red de seguridad: cualquier excepción no prevista responde 500 con un
     * mensaje genérico. El detalle completo solo va al log del servidor.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> errorInterno(Exception ex) {
        LOG.error("Error no controlado al procesar la petición", ex);
        return respuesta(HttpStatus.INTERNAL_SERVER_ERROR, List.of(MENSAJE_ERROR_INTERNO));
    }

    /** Los errores ya modelados por Spring conservan su estado y su detalle. */
    @ExceptionHandler(ErrorResponseException.class)
    public ResponseEntity<ErrorResponse> errorDeSpring(ErrorResponseException ex) {
        HttpStatus estado = HttpStatus.valueOf(ex.getStatusCode().value());
        return respuesta(estado, List.of(ex.getBody().getDetail()));
    }

    private ResponseEntity<ErrorResponse> respuesta(HttpStatus status, List<String> mensajes) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), mensajes));
    }
}
