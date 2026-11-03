package musarum.backend.enrichment.exception;

@SuppressWarnings("serial")
public class EnrichmentFailedException extends RuntimeException {
    public EnrichmentFailedException(String audioPath, Throwable cause) {
        super("No se pudo enriquecer: " + audioPath, cause);
    }
}