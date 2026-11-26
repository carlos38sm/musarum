package musarum.backend.admin;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import musarum.backend.song.Song;
import musarum.backend.song.SongMapper;
import musarum.backend.song.SongRepository;
import musarum.backend.song.dto.SongSummaryDto;

/**
 * Endpoint OWNER para listar canciones sin identificar. El reintento de
 * enriquecimiento ya existe en {@code SongController} (POST
 * {@code /songs/{id}/re-enrich}); desde aquí la pantalla OWNER del móvil
 * pide la lista y luego reutiliza ese otro endpoint para relanzar.
 */
@RestController
@RequestMapping("/admin/songs")
class UnidentifiedSongsController {

    private final SongRepository songRepository;
    private final SongMapper songMapper;

    UnidentifiedSongsController(SongRepository songRepository,
                                SongMapper songMapper) {
        this.songRepository = songRepository;
        this.songMapper = songMapper;
    }

    /**
     * Canciones que Emendatium no supo identificar O que viven en
     * {@code _inbox/sin-identificar/}. Orden desc por fecha de alta.
     */
    @GetMapping("/unidentified")
    @PreAuthorize("hasRole('OWNER')")
    @Transactional(readOnly = true)
    ResponseEntity<List<SongSummaryDto>> list() {
        List<Song> songs = songRepository.findUnidentified();
        List<SongSummaryDto> dtos = songs.stream()
                .map(songMapper::toSummary)
                .toList();
        return ResponseEntity.ok(dtos);
    }
}
