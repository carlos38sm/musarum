package musarum.backend.youtube;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de resolución YouTube. Lo exponemos bajo /songs/{id} por
 * cercanía al recurso, aunque la lógica vive en {@link YoutubeResolveService}
 * para mantener el paquete youtube/ como self-contained.
 */
@RestController
@RequestMapping("/songs")
class YoutubeController {

    private final YoutubeResolveService service;

    YoutubeController(YoutubeResolveService service) {
        this.service = service;
    }

    /**
     * Devuelve el videoId de YouTube para la canción. 200 con payload si lo
     * encontramos (cacheado o acabado de scrapear), 204 si no hay match
     * (el móvil mostrará "no se encontró vídeo"). Query param ?refresh=true
     * fuerza re-scraping ignorando el caché — útil cuando el user cree que
     * YouTube sugiere algo mejor ahora.
     */
    @GetMapping("/{id}/youtube-video")
    ResponseEntity<YoutubeVideoDto> resolve(
            @PathVariable Long id,
            @RequestParam(defaultValue = "false") boolean refresh) {
        String videoId = service.resolveFor(id, refresh);
        if (videoId == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(new YoutubeVideoDto(videoId));
    }

    record YoutubeVideoDto(String videoId) {}
}
