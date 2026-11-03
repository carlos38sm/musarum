package musarum.backend.favorite.dto;

import java.time.Instant;
import java.util.List;
import musarum.backend.song.dto.ArtistRef;

public record FavoriteDto(
        Long id,
        Long songId,
        String songTitle,
        Integer durationMs,
        List<ArtistRef> artists,
        String albumTitle,
        String coverUrl,
        Instant favoritedAt
) {}