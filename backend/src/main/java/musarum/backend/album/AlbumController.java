package musarum.backend.album;

import musarum.backend.album.dto.AlbumCardDto;
import musarum.backend.album.dto.AlbumDetailsDto;
import musarum.backend.shared.CoverStorage;
import musarum.backend.shared.ImageResponseBuilder;
import musarum.backend.song.SongService;
import musarum.backend.song.dto.SongSummaryDto;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/albums")
class AlbumController {

    private final AlbumService albumService;
    private final SongService songService;
    private final CoverStorage coverStorage;

    AlbumController(AlbumService albumService, SongService songService, CoverStorage coverStorage) {
        this.albumService = albumService;
        this.songService = songService;
        this.coverStorage = coverStorage;
    }

    @GetMapping
    ResponseEntity<Page<AlbumCardDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(albumService.findAll(pageable));
    }

    @GetMapping("/{id}")
    ResponseEntity<AlbumDetailsDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(albumService.findById(id));
    }

    @GetMapping("/{id}/songs")
    ResponseEntity<Page<SongSummaryDto>> findSongs(@PathVariable Long id, Pageable pageable) {
        return ResponseEntity.ok(songService.findByAlbum(id, pageable));
    }

    /** Sirve la portada del álbum guardada localmente (APIC extraído del audio). */
    @GetMapping("/{id}/cover")
    ResponseEntity<Resource> getCover(
            @PathVariable Long id,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch) {
        return coverStorage.findAlbumCover(id)
                .map(path -> ImageResponseBuilder.build(path, coverStorage.contentTypeFor(path), ifNoneMatch))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }
}
