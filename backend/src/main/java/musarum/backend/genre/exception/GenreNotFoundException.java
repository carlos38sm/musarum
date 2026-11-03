package musarum.backend.genre.exception;

@SuppressWarnings("serial")
public class GenreNotFoundException extends RuntimeException {
    public GenreNotFoundException(Long id) {
        super("No existe el género con id " + id);
    }
}