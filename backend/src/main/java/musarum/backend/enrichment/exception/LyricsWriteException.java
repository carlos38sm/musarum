package musarum.backend.enrichment.exception;

@SuppressWarnings("serial")
public class LyricsWriteException extends RuntimeException {
    public LyricsWriteException(Long songId, Throwable cause) {
        super("No se pudo guardar la letra de la canción " + songId, cause);
    }
}