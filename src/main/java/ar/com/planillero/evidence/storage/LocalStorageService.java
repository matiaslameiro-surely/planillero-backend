package ar.com.planillero.evidence.storage;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * Implementación de almacenamiento de objetos en sistema de archivos local con política estricta WORM.
 */
@Service
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalStorageService implements ObjectStorageService {

    private final Path rootLocation;

    public LocalStorageService(StorageProperties properties) {
        this.rootLocation = Paths.get(properties.getLocalDir()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.rootLocation);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo inicializar el directorio de almacenamiento local", e);
        }
    }

    @Override
    public void save(String objectKey, InputStream inputStream, long size, String contentType) {
        Path destinationFile = resolvePath(objectKey);

        if (Files.exists(destinationFile)) {
            throw new WormPolicyViolationException(
                    "Violación de política WORM: el objeto ya existe y no puede ser sobreescrito: " + objectKey);
        }

        try {
            // Asegurar directorio padre
            if (destinationFile.getParent() != null) {
                Files.createDirectories(destinationFile.getParent());
            }
            Files.copy(inputStream, destinationFile);
        } catch (FileAlreadyExistsException e) {
            throw new WormPolicyViolationException(
                    "Violación de política WORM: el archivo destino ya fue creado concurrentemente: " + objectKey);
        } catch (IOException e) {
            throw new IllegalStateException("Error al guardar archivo en almacenamiento local: " + objectKey, e);
        }
    }

    @Override
    public InputStream load(String objectKey) {
        Path file = resolvePath(objectKey);
        if (!Files.exists(file) || !Files.isReadable(file)) {
            throw new IllegalArgumentException("El objeto no existe o no se puede leer: " + objectKey);
        }
        try {
            return Files.newInputStream(file);
        } catch (IOException e) {
            throw new IllegalStateException("Error al abrir stream de lectura para objeto: " + objectKey, e);
        }
    }

    @Override
    public boolean exists(String objectKey) {
        Path file = resolvePath(objectKey);
        return Files.exists(file);
    }

    @Override
    public void delete(String objectKey) {
        Path file = resolvePath(objectKey);
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            // Log y continuar
        }
    }

    private Path resolvePath(String objectKey) {
        String sanitizedKey = objectKey.replace('\\', '/').trim();
        if (sanitizedKey.contains("..")) {
            throw new SecurityException("Intento de path traversal detectado en objectKey: " + objectKey);
        }
        Path destination = this.rootLocation.resolve(sanitizedKey).normalize();
        if (!destination.startsWith(this.rootLocation)) {
            throw new SecurityException("Intento de acceso fuera del directorio de almacenamiento: " + objectKey);
        }
        return destination;
    }
}
