package musarum.backend.enrichment.exception;

@SuppressWarnings("serial")
public class BatchEnrichmentException extends RuntimeException {
    public BatchEnrichmentException(String message, Throwable cause) {
        super(message, cause);
    }
}