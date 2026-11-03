package musarum.backend.playlist.exception;

@SuppressWarnings("serial")
public class SongAlreadyInPlaylistException extends RuntimeException {
    public SongAlreadyInPlaylistException(Long playlistId, Long songId) {
        super("La canción " + songId + " ya está en la playlist " + playlistId);
    }
}