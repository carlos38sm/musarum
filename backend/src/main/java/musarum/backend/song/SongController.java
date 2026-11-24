package musarum.backend.song;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import musarum.backend.enrichment.EmendatiumAutoEnricher;
import musarum.backend.shared.CoverStorage;
import musarum.backend.shared.VideoStorage;
import musarum.backend.shared.security.CurrentUserResolver;
import musarum.backend.song.dto.SongDetailsDto;
import musarum.backend.song.dto.SongSummaryDto;
import musarum.backend.song.dto.TagEditRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/songs")
class SongController {

    private final SongService songService;
    private final SongDeletionService songDeletionService;
    private final TagEditService tagEditService;
    private final CurrentUserResolver currentUser;
    private final VideoStorage videoStorage;
    private final EmendatiumAutoEnricher emendatiumEnricher;
    private final CoverStorage coverStorage;
    private final SongRepository songRepository;

    SongController(SongService songService,
                   SongDeletionService songDeletionService,
                   TagEditService tagEditService,
                   CurrentUserResolver currentUser,
                   VideoStorage videoStorage,
                   EmendatiumAutoEnricher emendatiumEnricher,
                   CoverStorage coverStorage,
                   SongRepository songRepository) {
        this.songService = songService;
        this.songDeletionService = songDeletionService;
        this.tagEditService = tagEditService;
        this.currentUser = currentUser;
        this.videoStorage = videoStorage;
        this.emendatiumEnricher = emendatiumEnricher;
        this.coverStorage = coverStorage;
        this.songRepository = songRepository;
    }

    @GetMapping
    ResponseEntity<Page<SongSummaryDto>> findAll(Pageable pageable) {
        return ResponseEntity.ok(songService.findAll(pageable));
    }

    /**
     * KPIs globales de la biblioteca: total de canciones + duración total
     * en ms. Lo consume el hero de SongsScreen para pintar "X pistas · Y h".
     * Público (cualquier user autenticado), no solo OWNER.
     */
    @GetMapping("/library-stats")
    LibraryStats libraryStats() {
        return new LibraryStats(
                songService.countAll(),
                songService.totalDurationMs()
        );
    }

    public record LibraryStats(long totalSongs, long totalDurationMs) {}

    @GetMapping("/{id}")
    ResponseEntity<SongDetailsDto> findById(@PathVariable Long id) {
        Long requesterId = currentUser.currentUserId();
        return ResponseEntity.ok(songService.findById(id, requesterId));
    }

    @GetMapping("/search")
    ResponseEntity<Page<SongSummaryDto>> search(@RequestParam String q, Pageable pageable) {
        return ResponseEntity.ok(songService.search(q, pageable));
    }

    @GetMapping("/shuffled")
    ResponseEntity<List<SongSummaryDto>> shuffled() {
        return ResponseEntity.ok(songService.findAllShuffled());
    }

    /**
     * Catálogo entero ordenado. El móvil lo usa para construir la cola del
     * player al tocar una canción desde Canciones, de modo que el botón
     * shuffle de NowPlaying barajee realmente las ~7000 canciones (y no la
     * ventana de ~100 ya cargadas).
     */
    @GetMapping("/all")
    ResponseEntity<List<SongSummaryDto>> all() {
        return ResponseEntity.ok(songService.findAllOrdered());
    }

    /**
     * Posición absoluta (0-indexed) de la primera canción cuyo título empieza
     * por cada letra, ordenando por título ascendente. Pensado para que el
     * AlphabetJumpBar del móvil pueda saltar a cualquier letra en una lista
     * paginada: al tocar "Z", la UI sabe en qué página vive esa canción
     * (page = offset / size) y a qué índice absoluto scrollear.
     *
     * Las canciones que empiezan por caracter no-alfabético (números,
     * símbolos) se agrupan bajo "#".
     */
    @GetMapping("/letter-offsets")
    ResponseEntity<Map<String, Integer>> letterOffsets() {
        return ResponseEntity.ok(songService.letterOffsets());
    }

    /**
     * Devuelve la posición 0-based de la canción en la lista paginada por
     * defecto. El móvil la usa para saltar al "ahora sonando" desde la
     * pantalla Canciones. 404 si no existe.
     */
    @GetMapping("/{id}/position")
    ResponseEntity<PositionDto> position(@PathVariable Long id) {
        return songService.findPosition(id)
                .map(idx -> ResponseEntity.ok(new PositionDto(idx)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    public record PositionDto(long index) {}

    /**
     * Modo "radio" desde una canción semilla: devuelve una cola de similares
     * (mismo género/artistas, con algo de aleatorio para que no quede
     * incestuoso). Pensado para que el móvil arranque una escucha infinita
     * tipo "Radio basada en esta canción".
     */
    @GetMapping("/{id}/radio")
    ResponseEntity<List<SongSummaryDto>> radio(@PathVariable Long id,
                                               @RequestParam(defaultValue = "50") int size) {
        return ResponseEntity.ok(songService.radioFrom(id, size));
    }

    /**
     * "Esta canción suena a…": canciones del catálogo con tonalidad
     * Camelot compatible y BPM ±8 respecto a la fuente. Si la fuente
     * no tiene BPM o musicalKey (Emendatium no la ha enriquecido aún),
     * devuelve lista vacía — el móvil oculta la sección.
     */
    @GetMapping("/{id}/similar")
    ResponseEntity<List<SongSummaryDto>> similar(@PathVariable Long id,
                                                 @RequestParam(defaultValue = "5") int size) {
        return ResponseEntity.ok(songService.findSimilar(id, size));
    }

    /**
     * Borra la canción del servidor por completo: fila de DB, referencias
     * (favoritos / playlists / historial) y el fichero del disco. Solo
     * OWNER puede hacerlo. Devuelve 204 incluso si el fichero del disco
     * no pudo borrarse — la DB queda limpia y el OWNER puede terminar el
     * limpieza a mano si Musarum no tenía permisos.
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        songDeletionService.deleteCompletely(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Sirve la portada propia de una canción (fichero físico en _covers/
     * song-{id}.ext). 404 si no hay custom cover subida — en ese caso el
     * móvil cae al cover del álbum (criterio del SongMapper).
     */
    @GetMapping("/{id}/cover")
    ResponseEntity<Resource> getCover(@PathVariable Long id) {
        return coverStorage.findSongCover(id)
                .map(this::buildCoverResponse)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Respuesta con Cache-Control público de 7 días para la portada propia.
     * El cache-busting del cliente lo hace SongMapper.effectiveCoverUrl
     * añadiendo ?v={updatedAtMs}.
     */
    private ResponseEntity<Resource> buildCoverResponse(Path path) {
        try {
            long length = java.nio.file.Files.size(path);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(coverStorage.contentTypeFor(path)))
                    .contentLength(length)
                    .cacheControl(org.springframework.http.CacheControl
                            .maxAge(java.time.Duration.ofDays(7)).cachePublic())
                    .body(new FileSystemResource(path));
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    /**
     * Sube una portada propia para esta canción (solo OWNER). Guarda el
     * fichero en _covers/song-{id}.ext, marca has_custom_cover=true y
     * devuelve el detalle actualizado — el cliente puede repintar con
     * el nuevo coverUrl inmediatamente.
     */
    @org.springframework.web.bind.annotation.PostMapping(
            value = "/{id}/cover",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    @PreAuthorize("hasRole('OWNER')")
    @org.springframework.transaction.annotation.Transactional
    ResponseEntity<SongDetailsDto> uploadCover(
            @PathVariable Long id,
            @org.springframework.web.bind.annotation.RequestPart("file")
            org.springframework.web.multipart.MultipartFile file
    ) throws IOException {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Canción no existe"));
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        String saved = coverStorage.saveSongCover(id, file.getBytes(), file.getContentType());
        if (saved == null) {
            return ResponseEntity.internalServerError().build();
        }
        song.setHasCustomCover(true);
        songRepository.save(song);
        return ResponseEntity.ok(
                songService.findById(id, currentUser.currentUserId())
        );
    }

    /**
     * Elimina la portada propia (vuelve al cover del álbum). Idempotente.
     */
    @org.springframework.web.bind.annotation.DeleteMapping("/{id}/cover")
    @PreAuthorize("hasRole('OWNER')")
    @org.springframework.transaction.annotation.Transactional
    ResponseEntity<SongDetailsDto> deleteCover(@PathVariable Long id) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.NOT_FOUND, "Canción no existe"));
        coverStorage.deleteSongCover(id);
        song.setHasCustomCover(false);
        songRepository.save(song);
        return ResponseEntity.ok(
                songService.findById(id, currentUser.currentUserId())
        );
    }

    /**
     * Encola un re-enriquecimiento Emendatium SOLO sobre esta canción.
     * Fire-and-forget (devuelve 202): los datos (bio artista, BPM, tonalidad,
     * replaygain, letras) aparecen poco a poco mientras Emendatium responde.
     * Útil desde la propia InfoScreen cuando falta algún campo concreto.
     * Solo OWNER puede dispararlo para no saturar Emendatium con peticiones
     * de los MEMBER.
     */
    @org.springframework.web.bind.annotation.PostMapping("/{id}/re-enrich")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<Void> reEnrich(@PathVariable Long id) {
        SongDetailsDto details = songService.findById(id, currentUser.currentUserId());
        emendatiumEnricher.enrichAsync(id, details.audioPath());
        return ResponseEntity.accepted().build();
    }

    /**
     * Edición manual de tags ID3 + metadatos JPA. Solo OWNER puede hacerlo
     * porque un cambio de artista crea uno nuevo si no existía — y los
     * MEMBER no deberían poder contaminar el catálogo con typos.
     * Reescribe el fichero del disco con jaudiotagger antes de tocar DB,
     * así si la reescritura falla la DB no queda desalineada.
     */
    @org.springframework.web.bind.annotation.PatchMapping("/{id}/tags")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<SongDetailsDto> editTags(@PathVariable Long id,
                                            @org.springframework.web.bind.annotation.RequestBody TagEditRequest request) {
        Long requesterId = currentUser.currentUserId();
        return ResponseEntity.ok(tagEditService.editTags(id, requesterId, request));
    }

    /**
     * Devuelve la letra. Si no hay (ni local ni LrcLib la encontró) → 200 OK
     * con cuerpo vacío. Evita 404s que confunden al interceptor del móvil y
     * problemas de content negotiation entre text/plain y los ApiError JSON.
     */
    @GetMapping(value = "/{id}/lyrics", produces = MediaType.TEXT_PLAIN_VALUE)
    ResponseEntity<String> lyrics(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(songService.getLyrics(id));
        } catch (musarum.backend.song.exception.LyricsNotFoundException e) {
            return ResponseEntity.ok("");
        }
    }

    /**
     * Fuerza re-búsqueda online de la letra ignorando la cacheada en disco.
     * Pensado para el botón "Buscar letra de nuevo" del móvil cuando la que
     * hay es la equivocada o quedó incompleta. Si la nueva búsqueda tampoco
     * encuentra, devolvemos 204 (sin contenido) y el móvil mantiene la que
     * ya tenía — no borramos ciegamente lo que al menos funciona.
     */
    @org.springframework.web.bind.annotation.PostMapping(
            value = "/{id}/lyrics/refresh",
            produces = MediaType.TEXT_PLAIN_VALUE
    )
    ResponseEntity<String> refreshLyrics(@PathVariable Long id) {
        try {
            return ResponseEntity.ok(songService.refreshLyrics(id));
        } catch (musarum.backend.song.exception.LyricsNotFoundException e) {
            return ResponseEntity.noContent().build();
        }
    }

    /**
     * Devuelve el vídeo local asociado a la canción si existe en
     * {@code _videos/} (p. ej. {@code 42.mp4}). Si no, 404 y el móvil oculta
     * el modo Vídeo de la píldora.
     */
    @GetMapping("/{id}/video")
    ResponseEntity<Resource> video(@PathVariable Long id) {
        // Pasamos el audioPath para que VideoStorage también pueda match-ear
        // por nombre del fichero original — así el usuario puede soltar
        // "Rihanna - Diamonds.mp4" en vez de renombrar al id.
        String audioPath = null;
        try {
            audioPath = songService.getAudioPath(id);
        } catch (Exception ignored) {
            // Si no existe la canción, pasamos null y seguimos → 404.
        }
        return videoStorage.findVideo(id, audioPath)
                .map(path -> buildVideoResponse(path, videoStorage.contentTypeFor(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<Resource> buildVideoResponse(Path file, String contentType) {
        try {
            long length = Files.size(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(length)
                    .body(new FileSystemResource(file));
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}