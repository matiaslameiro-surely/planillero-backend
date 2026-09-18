package ar.com.planillero.evidence.storage;

/**
 * Excepción lanzada cuando se intenta sobreescribir un archivo violando la política WORM (Write Once, Read Many).
 */
public class WormPolicyViolationException extends RuntimeException {

    public WormPolicyViolationException(String message) {
        super(message);
    }
}
