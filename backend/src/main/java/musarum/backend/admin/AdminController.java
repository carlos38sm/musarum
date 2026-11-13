package musarum.backend.admin;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de administración (OWNER-only). Hoy solo expone el snapshot de
 * estadísticas; el día que haya más (logs, triggers manuales, etc.) viven
 * bajo el mismo prefijo {@code /admin/server/*}.
 *
 * <p>{@code @PreAuthorize} se aplica a cada método en vez de a la clase: en
 * algunas combinaciones de Spring Security 6 + CGLIB + clases
 * package-private el proxy a nivel de clase se evalúa de forma inconsistente
 * (vimos 403 esporádicos en re-enrich mientras wipe-library funcionaba
 * contra la misma anotación de clase). A método siempre es evaluado.
 */
@RestController
@RequestMapping("/admin/server")
class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    private final ServerStatsService statsService;

    AdminController(ServerStatsService statsService) {
        this.statsService = statsService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('OWNER')")
    ServerStatsDto stats() {
        return statsService.buildSnapshot();
    }

    /** Borra ficheros .lrc en {@code _lyrics/} cuyo id ya no existe en {@code songs}. */
    @PostMapping("/clean-orphan-lyrics")
    @PreAuthorize("hasRole('OWNER')")
    DeleteResult cleanOrphanLyrics() {
        logCaller("clean-orphan-lyrics");
        int deleted = statsService.deleteOrphanLyricFiles();
        return new DeleteResult(deleted);
    }

    /**
     * Vacía el catálogo musical entero (canciones + artistas + álbumes + géneros
     * y sus referencias). Deja intactos usuarios, playlists y carpetas
     * configuradas. Pensado para re-arrancar con una carpeta de música nueva.
     */
    @PostMapping("/wipe-library")
    @PreAuthorize("hasRole('OWNER')")
    ServerStatsService.WipeSummary wipeLibrary() {
        logCaller("wipe-library");
        return statsService.wipeLibrary();
    }

    /**
     * Re-lanza enriquecimiento Emendatium sobre todas las canciones. Devuelve
     * cuántas se encolaron. Los datos (bio, BPM, tonalidad, replaygain) van
     * apareciendo conforme Emendatium responde (fire-and-forget).
     */
    @PostMapping("/re-enrich-all")
    @PreAuthorize("hasRole('OWNER')")
    ServerStatsService.ReEnrichSummary reEnrichAll() {
        logCaller("re-enrich-all");
        return statsService.reEnrichAll();
    }

    /**
     * Barre artistas / álbumes / géneros sin canciones. Útiles tras borrados
     * masivos: aparecían "fantasmas" en ArtistsScreen con 0 canciones.
     */
    @PostMapping("/clean-orphan-entities")
    @PreAuthorize("hasRole('OWNER')")
    ServerStatsService.CleanOrphansSummary cleanOrphanEntities() {
        logCaller("clean-orphan-entities");
        return statsService.cleanOrphanEntities();
    }

    /** Traza quién llamó y con qué authorities — útil para diagnosticar 403. */
    private void logCaller(String action) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.info("Admin action [{}] por user={} authorities={}",
                action,
                auth != null ? auth.getName() : "<anon>",
                auth != null ? auth.getAuthorities() : "[]");
    }

    public record DeleteResult(int deleted) {}
}
