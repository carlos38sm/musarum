package musarum.backend.enrichment.dto;

public record BatchResultLine(
        String fileName,
        String status,
        EnrichedMetadata metadata,
        EnrichmentSuggestions suggestions,
        String error
) {}