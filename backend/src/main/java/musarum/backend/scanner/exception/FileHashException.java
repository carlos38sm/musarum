package musarum.backend.scanner.exception;

import java.nio.file.Path;

@SuppressWarnings("serial")
public class FileHashException extends RuntimeException {
    public FileHashException(Path filePath, Throwable cause) {
        super("No se pudo calcular el hash de: " + filePath, cause);
    }
}