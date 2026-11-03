package musarum.backend.song.exception;

@SuppressWarnings("serial")
public class SongNotFoundException extends RuntimeException{
	public SongNotFoundException(Long id) {
		super("NO EXISTE CANCIÓN CON ID: "+ id);
	}
}