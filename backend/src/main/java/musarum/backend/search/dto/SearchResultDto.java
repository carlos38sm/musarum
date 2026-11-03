package musarum.backend.search.dto;

import java.util.List;
import musarum.backend.album.dto.AlbumCardDto;
import musarum.backend.artist.dto.ArtistSummaryDto;
import musarum.backend.song.dto.SongSummaryDto;

public record SearchResultDto(
        List<ArtistSummaryDto> artists,
        List<AlbumCardDto> albums,
        List<SongSummaryDto> songs
) {}
