package musarum.backend.artist.dto;

public record ArtistDetailsDto(
		Long id,
		String name,
		String sortName,
		String mbId,
		String country,
		Integer beginYear,
		String biography,
		String imageUrl,
		String type,
		long songCount,
		long albumCount,
		long totalDurationMs
) {}
