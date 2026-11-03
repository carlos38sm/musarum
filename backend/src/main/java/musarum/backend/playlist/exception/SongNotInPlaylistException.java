package musarum.backend.playlist.exception;

@SuppressWarnings("serial")
public class SongNotInPlaylistException extends RuntimeException {
    public SongNotInPlaylistException(Long playlistId, Long entryId) {
        super("La entrada " + entryId + " no pertenece a la playlist " + playlistId);
    }
}