package musarum.backend.genre;

import org.springframework.stereotype.Component;
import musarum.backend.genre.dto.GenreResponse;

@Component
class GenreMapper {
	
	GenreResponse toResponse(Genre genre) {
		return new GenreResponse(genre.getId(), genre.getName());
	}
}