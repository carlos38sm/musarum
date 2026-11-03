package musarum.backend.song.exception;

public class LyricsNotFoundException extends RuntimeException {
    public LyricsNotFoundException(Long songId) {
        super("No hay letra para la canción " + songId);
    }
}
