package musarum.backend.enrichment.dto;

public record ArtistInfo(
        String biography,
        String country,
        Integer formedYear
) {}