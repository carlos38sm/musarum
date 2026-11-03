package musarum.backend.scanner.exception;

import java.nio.file.Path;

@SuppressWarnings("serial")
public class ScanFailedException extends RuntimeException {
    public ScanFailedException(Path folder, Throwable cause) {
        super("No se pudo escanear la carpeta: " + folder, cause);
    }
}