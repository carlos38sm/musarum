package musarum.backend.enrichment.dto;

public record CoverArt(
        String sourceUrl,
        Integer width,
        Integer height
) {}