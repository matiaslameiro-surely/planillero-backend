package ar.com.planillero.common;

import org.springframework.http.HttpStatus;

/**
 * Error de negocio que se traduce a una respuesta HTTP con un código estable.
 *
 * <p>El {@code code} es para el cliente (o para los tests) y no cambia con el texto: permite
 * reaccionar al caso sin depender del mensaje en español.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;

    public ApiException(HttpStatus status, String code, String message) {
        super(message);
        this.status = status;
        this.code = code;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getCode() {
        return code;
    }

    /** 401: credenciales o tokens inválidos. */
    public static ApiException unauthorized(String code, String message) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, message);
    }

    /** 400: pedido mal formado. */
    public static ApiException badRequest(String code, String message) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, message);
    }

    /** 404: el recurso pedido no existe. */
    public static ApiException notFound(String code, String message) {
        return new ApiException(HttpStatus.NOT_FOUND, code, message);
    }

    /** 409: el recurso ya está en el estado que se quería alcanzar. */
    public static ApiException conflict(String code, String message) {
        return new ApiException(HttpStatus.CONFLICT, code, message);
    }

    /** 429: demasiados intentos; se frena para no facilitar la fuerza bruta. */
    public static ApiException tooManyRequests(String code, String message) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, code, message);
    }
}
