package musarum.backend.artist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import musarum.backend.album.AlbumService;
import musarum.backend.album.dto.AlbumCardDto;
import musarum.backend.artist.dto.ArtistDetailsDto;
import musarum.backend.artist.dto.ArtistSummaryDto;
import musarum.backend.shared.CoverStorage;
import musarum.backend.song.SongService;
import musarum.backend.song.dto.SongSummaryDto;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/artists")
class ArtistController {

    private final ArtistService artistService;
    private final AlbumService albumService;
    private final SongService songService;
    private final CoverStorage coverStorage;

    ArtistController(ArtistService artistService,
                     AlbumService albumService,
                     SongService songService,
                     CoverStorage coverStorage) {
        this.artistService = artistService;
        this.albumService = albumService;
        this.songService = songService;
        this.coverStorage = coverStorage;
    }

    @GetMapping
    ResponseEntity<Page<ArtistSummaryDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(artistService.findAll(pageable));
    }

    @GetMapping("/{id}")
    ResponseEntity<ArtistDetailsDto> findById(@PathVariable Long id) {
        return ResponseEntity.ok(artistService.findById(id));
    }

    @GetMapping("/{id}/albums")
    ResponseEntity<Page<AlbumCardDto>> findAlbums(@PathVariable Long id, Pageable pageable) {
        return ResponseEntity.ok(albumService.findByArtist(id, pageable));
    }

    @GetMapping("/{id}/songs")
    ResponseEntity<Page<SongSummaryDto>> findSongs(@PathVariable Long id, Pageable pageable) {
        return ResponseEntity.ok(songService.findByArtist(id, pageable));
    }

    /** Sirve la imagen del artista guardada localmente (APIC extraído de un audio del artista). */
    @GetMapping("/{id}/image")
    ResponseEntity<Resource> getImage(@PathVariable Long id) {
        return coverStorage.findArtistImage(id)
                .map(path -> buildImageResponse(path, coverStorage.contentTypeFor(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<Resource> buildImageResponse(Path file, String contentType) {
        try {
            long length = Files.size(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(length)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                    .body(new FileSystemResource(file));
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}