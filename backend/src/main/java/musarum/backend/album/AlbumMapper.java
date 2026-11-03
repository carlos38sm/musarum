package musarum.backend.album;

import org.springframework.stereotype.Component;

import musarum.backend.album.dto.AlbumCardDto;
import musarum.backend.album.dto.AlbumDetailsDto;
import musarum.backend.album.dto.AlbumSummaryDto;

@Component
class AlbumMapper {

	AlbumSummaryDto toSummary(Album album) {
		return new AlbumSummaryDto(
				album.getId(),
				album.getTitle(),
				album.getCoverUrl()
				);
	}

	AlbumCardDto toCard(Album album) {
		return toCard(album, 0L, 0L);
	}

	AlbumCardDto toCard(Album album, long songCount, long totalDurationMs) {
		return new AlbumCardDto(
				album.getId(),
				album.getTitle(),
				album.getCoverUrl(),
				album.getArtist().getName(),
				album.getArtist().getId(),
				album.getReleaseYear(),
				songCount,
				totalDurationMs,
				inferType(album.getType(), songCount)
				);
	}

	AlbumDetailsDto toDetails(Album album) {
		return toDetails(album, 0L, 0L);
	}

	AlbumDetailsDto toDetails(Album album, long songCount, long totalDurationMs) {
		return new AlbumDetailsDto(
				album.getId(),
				album.getTitle(),
				album.getReleaseYear(),
				album.getType(),
				album.getCoverUrl(),
				album.getMbId(),
				album.getArtist().getName(),
				album.getArtist().getId(),
				songCount,
				totalDurationMs,
				inferType(album.getType(), songCount)
				);
	}

	/**
	 * Preferimos el {@code type} que trae el álbum (si Emendatium lo pobló).
	 * Si no, lo deducimos por nº de canciones (1 → sencillo, 2–6 → EP, 7+ → álbum).
	 */
	private String inferType(String storedType, long songCount) {
		if (storedType != null && !storedType.isBlank()) {
			return storedType;
		}
		if (songCount <= 0) return null;
		if (songCount == 1) return "Sencillo";
		if (songCount <= 6) return "EP";
		return "Álbum";
	}
}
