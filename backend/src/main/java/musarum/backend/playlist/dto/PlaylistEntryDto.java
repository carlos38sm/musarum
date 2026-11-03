package musarum.backend.playlist.dto;

import musarum.backend.song.dto.ArtistRef;
import java.util.List;

public record PlaylistEntryDto(
        Long entryId,
        Integer position,
        Long songId,
        String songTitle,
        Integer durationMs,
        List<ArtistRef> artists,
        String albumTitle,
        String coverUrl,
        String musicalKey
) {}