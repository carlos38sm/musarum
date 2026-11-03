package musarum.backend.song.dto;

import java.util.List;

public record SongCardDto(
        Long id,
        String title,
        Integer durationMs,
        List<ArtistRef> artists,
        String albumTitle,
        Long albumId,
        String coverUrl
) {}