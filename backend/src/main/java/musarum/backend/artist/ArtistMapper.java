package musarum.backend.artist;

import org.springframework.stereotype.Component;
import musarum.backend.artist.dto.ArtistDetailsDto;
import musarum.backend.artist.dto.ArtistSummaryDto;

@Component
class ArtistMapper {

	ArtistSummaryDto toSummary(Artist artist) {
		return new ArtistSummaryDto(
				artist.getId(),
				artist.getName(),
				artist.getImageUrl()
				);
	}

	ArtistDetailsDto toDetails(Artist artist) {
		return toDetails(artist, 0L, 0L, 0L);
	}

	ArtistDetailsDto toDetails(Artist artist, long songCount, long albumCount, long totalDurationMs) {
		return new ArtistDetailsDto(
				artist.getId(),
				artist.getName(),
				artist.getSortName(),
				artist.getMbId(),
				artist.getCountry(),
				artist.getBeginYear(),
				artist.getBiography(),
				artist.getImageUrl(),
				artist.getType(),
				songCount,
				albumCount,
				totalDurationMs
				);
	}
}
