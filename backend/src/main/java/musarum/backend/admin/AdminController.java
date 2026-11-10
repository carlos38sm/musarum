package musarum.backend.admin;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de administración (OWNER-only). Hoy solo expone el snapshot de
 * estadísticas; el día que haya más (logs, triggers manuales, etc.) viven
 * bajo el mismo prefijo {@code /admin/server/*}.
 */
@RestController
@RequestMapping("/admin/server")
@PreAuthorize("hasRole('OWNER')")
class AdminController {

    private final ServerStatsService statsService;

    AdminController(ServerStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    ServerStatsDto stats() {
        return statsService.buildSnapshot();
    }

    /** Borra ficheros .lrc en {@code _lyrics/} cuyo id ya no existe en {@code songs}. */
    @org.springframework.web.bind.annotation.PostMapping("/clean-orphan-lyrics")
    DeleteResult cleanOrphanLyrics() {
        int deleted = statsService.deleteOrphanLyricFiles();
        return new DeleteResult(deleted);
    }

    /**
     * Vacía el catálogo musical entero (canciones + artistas + álbumes + géneros
     * y sus referencias). Deja intactos usuarios, playlists y carpetas
     * configuradas. Pensado para re-arrancar con una carpeta de música nueva.
     */
    @org.springframework.web.bind.annotation.PostMapping("/wipe-library")
    ServerStatsService.WipeSummary wipeLibrary() {
        return statsService.wipeLibrary();
    }

    /**
     * Re-lanza enriquecimiento Emendatium sobre todas las canciones. Devuelve
     * cuántas se encolaron. Los datos (bio, BPM, tonalidad, replaygain) van
     * apareciendo conforme Emendatium responde (fire-and-forget).
     */
    @org.springframework.web.bind.annotation.PostMapping("/re-enrich-all")
    ServerStatsService.ReEnrichSummary reEnrichAll() {
        return statsService.reEnrichAll();
    }

    public record DeleteResult(int deleted) {}
}
