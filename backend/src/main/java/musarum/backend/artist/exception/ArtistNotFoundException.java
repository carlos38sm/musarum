package musarum.backend.artist.exception;

@SuppressWarnings("serial")
public class ArtistNotFoundException extends RuntimeException{
    public ArtistNotFoundException(Long id) {
        super("NO EXISTE ARTISTA CON ID: " + id);
    }
}