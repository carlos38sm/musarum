package musarum.backend.favorite.exception;

@SuppressWarnings("serial")
public class SongAlreadyFavoritedException extends RuntimeException {
    public SongAlreadyFavoritedException(Long songId) {
        super("La canción " + songId + " ya está en favoritos");
    }
}