package musarum.backend.scanner.exception;

@SuppressWarnings("serial")
public class TagReadException extends RuntimeException {
    public TagReadException(String audioPath, Throwable cause) {
        super("No se pudieron leer las etiquetas de: " + audioPath, cause);
    }
}