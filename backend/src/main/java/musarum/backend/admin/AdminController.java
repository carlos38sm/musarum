package musarum.backend.admin;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import musarum.backend.backups.BackupService;

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
    private final BackupService backupService;
    private final HostStatsService hostStatsService;

    AdminController(ServerStatsService statsService,
                    BackupService backupService,
                    HostStatsService hostStatsService) {
        this.statsService = statsService;
        this.backupService = backupService;
        this.hostStatsService = hostStatsService;
    }

    @GetMapping("/stats")
    @PreAuthorize("hasRole('OWNER')")
    ServerStatsDto stats() {
        return statsService.buildSnapshot();
    }

    /**
     * Métricas del SISTEMA (no de Musarum): CPU, RAM física, swap, disco IO,
     * red por interfaz. El móvil lo refresca cada pocos segundos cuando el
     * OWNER tiene el "modo live" encendido en Análisis del servidor.
     */
    @GetMapping("/host-stats")
    @PreAuthorize("hasRole('OWNER')")
    HostStatsService.HostStats hostStats() {
        return hostStatsService.snapshot();
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
     * Progreso del "Re-enriquecer todo" en curso. El móvil hace polling
     * aquí mientras running=true para pintar la barra. No cambia nada,
     * solo devuelve contadores — es barato.
     */
    @GetMapping("/enrich-progress")
    @PreAuthorize("hasRole('OWNER')")
    musarum.backend.enrichment.EnrichmentProgress.EnrichmentProgressDto enrichProgress() {
        return statsService.reEnrichProgress();
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

    // ── Backups ──────────────────────────────────────────────────────

    @GetMapping("/backups")
    @PreAuthorize("hasRole('OWNER')")
    List<BackupService.BackupFileDto> listBackups() throws IOException {
        return backupService.list();
    }

    @PostMapping("/backups")
    @PreAuthorize("hasRole('OWNER')")
    BackupService.BackupFileDto createBackup() throws IOException {
        logCaller("backup-now");
        Path p = backupService.snapshot();
        return new BackupService.BackupFileDto(
                p.getFileName().toString(),
                Files.size(p),
                Files.getLastModifiedTime(p).toInstant());
    }

    @GetMapping("/backups/{fileName}")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<FileSystemResource> downloadBackup(@PathVariable String fileName) {
        Path p = backupService.resolveSafe(fileName);
        if (p == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + fileName + "\"")
                .body(new FileSystemResource(p));
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
