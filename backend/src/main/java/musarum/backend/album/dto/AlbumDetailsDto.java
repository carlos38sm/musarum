package musarum.backend.album.dto;

public record AlbumDetailsDto(
		Long id,
		String title,
		Integer releaseYear,
		String type,
		String coverUrl,
		String mbId,
		String artistName,
		Long artistId,
		long songCount,
		long totalDurationMs,
		String inferredType
) {}