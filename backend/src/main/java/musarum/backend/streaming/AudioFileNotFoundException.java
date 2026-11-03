package musarum.backend.streaming;

@SuppressWarnings("serial")
public class AudioFileNotFoundException extends RuntimeException {
    public AudioFileNotFoundException(Long songId) {
        super("El archivo de audio de la canción " + songId + " no está disponible");
    }
}