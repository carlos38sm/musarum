package musarum.backend.album.dto;

public record AlbumCardDto(
		Long id,
		String title,
		String coverUrl,
		String artistName,
		Long artistId,
		Integer releaseYear,
		long songCount,
		long totalDurationMs,
		String inferredType
) {}