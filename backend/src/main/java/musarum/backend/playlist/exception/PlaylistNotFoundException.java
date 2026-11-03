package musarum.backend.playlist.exception;

@SuppressWarnings("serial")
public class PlaylistNotFoundException extends RuntimeException {
    public PlaylistNotFoundException(Long id) {
        super("No existe la playlist con id " + id);
    }
}