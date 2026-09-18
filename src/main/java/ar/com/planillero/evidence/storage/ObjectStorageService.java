package ar.com.planillero.evidence.storage;

import java.io.InputStream;

/**
 * Interfaz de almacenamiento de evidencias periciales con soporte de política WORM (Write Once, Read Many).
 */
public interface ObjectStorageService {

    /**
     * Guarda un objeto binario en el almacenamiento.
     * Si el objeto ya existe, debe lanzar {@link WormPolicyViolationException} respetando la política WORM.
     */
    void save(String objectKey, InputStream inputStream, long size, String contentType);

    /**
     * Recupera el stream de lectura de un objeto pericial.
     */
    InputStream load(String objectKey);

    /**
     * Verifica la existencia del objeto.
     */
    boolean exists(String objectKey);

    /**
     * Elimina un objeto únicamente para operaciones de rollback técnico durante fallos de ingesta.
     */
    void delete(String objectKey);
}
