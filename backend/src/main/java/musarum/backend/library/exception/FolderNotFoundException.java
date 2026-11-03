package musarum.backend.library.exception;

public class FolderNotFoundException extends RuntimeException {
    public FolderNotFoundException(Long id) {
        super("Carpeta " + id + " no encontrada");
    }
}
