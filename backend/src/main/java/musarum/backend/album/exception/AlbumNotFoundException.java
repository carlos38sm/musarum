package musarum.backend.album.exception;

@SuppressWarnings("serial")
public class AlbumNotFoundException extends RuntimeException{
	public AlbumNotFoundException(Long id) {
		super("NO EXISTE ALBUM CON ESE ID: "+ id);
	}
}