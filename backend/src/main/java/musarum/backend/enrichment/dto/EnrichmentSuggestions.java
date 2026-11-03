package musarum.backend.enrichment.dto;

public record EnrichmentSuggestions(
        String fileName,
        String title,
        boolean hasIdentity
) {}