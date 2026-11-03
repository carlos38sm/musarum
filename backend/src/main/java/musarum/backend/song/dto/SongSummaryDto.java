package musarum.backend.song.dto;

import java.util.List;

public record SongSummaryDto(
        Long id,
        String title,
        Integer durationMs,
        Integer trackNumber,
        List<ArtistRef> artists,
        String coverUrl
) {}