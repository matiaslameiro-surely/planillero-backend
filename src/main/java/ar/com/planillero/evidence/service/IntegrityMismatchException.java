package ar.com.planillero.evidence.service;

/**
 * Excepción lanzada cuando el hash declarado por el cliente difiere del calculado en streaming por el servidor.
 */
public class IntegrityMismatchException extends RuntimeException {

    public IntegrityMismatchException(String message) {
        super(message);
    }
}
