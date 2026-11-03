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

    public record DeleteResult(int deleted) {}
}
