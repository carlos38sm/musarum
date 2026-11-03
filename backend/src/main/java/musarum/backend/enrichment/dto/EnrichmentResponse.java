package musarum.backend.enrichment.dto;

public record EnrichmentResponse(
        EnrichedMetadata metadata,
        EnrichmentSuggestions suggestions
) {}