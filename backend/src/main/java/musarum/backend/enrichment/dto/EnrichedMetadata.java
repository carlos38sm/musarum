package musarum.backend.enrichment.dto;

public record EnrichedMetadata(
        String title,
        String artist,
        String album,
        Integer year,
        Integer durationMs,
        String isrc,
        Integer bpm,
        String musicalKey,
        Double replayGainTrackGain,
        String mbArtistId,
        String mbReleaseId,
        ArtistInfo artistInfo,
        CoverArt coverArt,
        String syncedLyrics
) {}