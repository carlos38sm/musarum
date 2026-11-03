package musarum.backend.genre;

import java.util.List;
import musarum.backend.genre.dto.GenreResponse;
import musarum.backend.song.SongService;
import musarum.backend.song.dto.SongSummaryDto;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/genres")
class GenreController {

    private final GenreService genreService;
    private final SongService songService;

    GenreController(GenreService genreService, SongService songService) {
        this.genreService = genreService;
        this.songService = songService;
    }

    @GetMapping
    ResponseEntity<List<GenreResponse>> findAll() {
        return ResponseEntity.ok(genreService.findAll());
    }

    @GetMapping("/{id}")
    ResponseEntity<GenreResponse> findById(@PathVariable Long id) {
        return ResponseEntity.ok(genreService.findById(id));
    }

    @GetMapping("/{id}/songs")
    ResponseEntity<Page<SongSummaryDto>> songs(
            @PathVariable Long id,
            Pageable pageable
    ) {
        return ResponseEntity.ok(songService.findByGenre(id, pageable));
    }
}