package musarum.backend.favorite.exception;

@SuppressWarnings("serial")
public class FavoriteNotFoundException extends RuntimeException {
    public FavoriteNotFoundException(Long songId) {
        super("La canción " + songId + " no está en favoritos");
    }
}