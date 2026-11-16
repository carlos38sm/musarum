package musarum.backend.backups;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import musarum.backend.favorite.FavoriteRepository;
import musarum.backend.playlist.PlaylistEntryRepository;
import musarum.backend.playlist.PlaylistRepository;

/**
 * Backups automáticos de estado de usuario. Cada 30 minutos vuelca
 * playlists + entries + favoritos a {@code _backups/musarum-YYYY-MM-DD_HHMM.json}
 * y mantiene los últimos N. Reproducciones NO se incluyen — demasiado grande
 * y Carlos ha indicado que son prescindibles.
 *
 * El OWNER puede descargar el último backup o pedir uno al momento desde
 * Server Stats (bloque de "Mantenimiento"). No incluimos restore automático
 * — la restauración manual se hace reemplazando la DB o importando desde
 * la UI cuando toque (futuro).
 */
@Service
public class BackupService {

    private static final Logger log = LoggerFactory.getLogger(BackupService.class);
    private static final int KEEP_COPIES = 10;
    private static final DateTimeFormatter TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd_HHmm");

    private final PlaylistRepository playlistRepository;
    private final PlaylistEntryRepository playlistEntryRepository;
    private final FavoriteRepository favoriteRepository;
    private final Path backupsRoot;
    private final ObjectMapper mapper;

    BackupService(@Value("${MUSARUM_MUSIC_PATH}") String musicPath,
                  PlaylistRepository playlistRepository,
                  PlaylistEntryRepository playlistEntryRepository,
                  FavoriteRepository favoriteRepository) {
        this.playlistRepository = playlistRepository;
        this.playlistEntryRepository = playlistEntryRepository;
        this.favoriteRepository = favoriteRepository;
        this.backupsRoot = Path.of(musicPath).resolve("_backups");
        this.mapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT)
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        try {
            Files.createDirectories(backupsRoot);
            log.info("BackupService inicializado en: {}", backupsRoot);
        } catch (IOException e) {
            log.error("No pude crear la carpeta de backups: {}", e.getMessage());
        }
    }

    /**
     * Snapshot programado cada 30 minutos (initialDelay 2 min para no pegar
     * un backup justo al arrancar el contenedor, cuando la DB aún se podría
     * estar inicializando o migrando con Flyway).
     */
    @Scheduled(initialDelayString = "PT2M", fixedRateString = "PT30M")
    public void scheduled() {
        try {
            Path written = snapshot();
            log.info("Backup automático escrito: {}", written.getFileName());
            rotate();
        } catch (Exception e) {
            log.error("Fallo en backup programado: {}", e.getMessage(), e);
        }
    }

    /** Genera un backup al momento y devuelve la ruta. Idempotente dentro del minuto. */
    @Transactional(readOnly = true)
    public Path snapshot() throws IOException {
        if (!Files.exists(backupsRoot)) Files.createDirectories(backupsRoot);
        String ts = LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()).format(TS_FMT);
        Path file = backupsRoot.resolve("musarum-" + ts + ".json");

        BackupDto dto = buildDto();
        byte[] bytes = mapper.writeValueAsBytes(dto);
        Files.write(file, bytes,
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING,
                StandardOpenOption.WRITE);
        return file;
    }

    /** Lista los backups ordenados del más reciente al más antiguo. */
    public List<BackupFileDto> list() throws IOException {
        if (!Files.exists(backupsRoot)) return List.of();
        try (Stream<Path> files = Files.list(backupsRoot)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(".json"))
                    .sorted(Comparator.comparing(Path::getFileName).reversed())
                    .map(p -> new BackupFileDto(
                            p.getFileName().toString(),
                            runCatchingSize(p),
                            runCatchingMtime(p)))
                    .toList();
        }
    }

    /** Ruta local de un backup por nombre, o null si no existe/sale de la carpeta. */
    public Path resolveSafe(String fileName) {
        if (fileName == null || fileName.contains("/") || fileName.contains("\\")
                || fileName.contains("..")) return null;
        Path p = backupsRoot.resolve(fileName).normalize();
        if (!p.startsWith(backupsRoot) || !Files.exists(p)) return null;
        return p;
    }

    /** Mantiene los últimos N backups; borra el resto. */
    private void rotate() {
        try {
            List<BackupFileDto> all = list();
            if (all.size() <= KEEP_COPIES) return;
            for (int i = KEEP_COPIES; i < all.size(); i++) {
                Path p = backupsRoot.resolve(all.get(i).fileName());
                runCatchingDelete(p);
            }
        } catch (IOException e) {
            log.warn("No pude listar backups para rotar: {}", e.getMessage());
        }
    }

    private BackupDto buildDto() {
        // Entries agrupadas por playlist_id, pre-ordenadas por position.
        Map<Long, List<BackupDto.EntryBackup>> entriesByPlaylist = new HashMap<>();
        for (Object[] row : playlistEntryRepository.dumpAll()) {
            Long playlistId = ((Number) row[0]).longValue();
            Long songId = ((Number) row[1]).longValue();
            Integer position = ((Number) row[2]).intValue();
            entriesByPlaylist
                    .computeIfAbsent(playlistId, k -> new ArrayList<>())
                    .add(new BackupDto.EntryBackup(songId, position));
        }

        List<BackupDto.PlaylistBackup> pls = new ArrayList<>();
        for (Object[] row : playlistRepository.dumpAll()) {
            Long id = ((Number) row[0]).longValue();
            String name = (String) row[1];
            String description = (String) row[2];
            boolean isPublic = (Boolean) row[3];
            Long ownerId = row[4] != null ? ((Number) row[4]).longValue() : null;
            pls.add(new BackupDto.PlaylistBackup(
                    id, name, description, isPublic, ownerId,
                    entriesByPlaylist.getOrDefault(id, List.of())));
        }

        // Favoritos: query nativa, (user_id, song_id, created_at).
        List<Object[]> favRows = favoriteRepository.dumpAll();
        List<BackupDto.FavoriteBackup> favs = new ArrayList<>(favRows.size());
        for (Object[] row : favRows) {
            Long userId = ((Number) row[0]).longValue();
            Long songId = ((Number) row[1]).longValue();
            Instant createdAt = toInstantOrNull(row[2]);
            favs.add(new BackupDto.FavoriteBackup(userId, songId, createdAt));
        }

        return new BackupDto(
                "1",                // schema version
                Instant.now(),
                pls,
                favs);
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private static long runCatchingSize(Path p) {
        try { return Files.size(p); } catch (IOException e) { return 0L; }
    }

    private static Instant runCatchingMtime(Path p) {
        try { return Files.getLastModifiedTime(p).toInstant(); } catch (IOException e) { return null; }
    }

    private static void runCatchingDelete(Path p) {
        try { Files.deleteIfExists(p); } catch (IOException ignored) { }
    }

    /** Postgres JDBC puede devolver Timestamp o OffsetDateTime según driver. */
    private static Instant toInstantOrNull(Object o) {
        if (o == null) return null;
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        if (o instanceof java.time.Instant it) return it;
        return null;
    }

    // ── DTOs ──────────────────────────────────────────────────────────

    public record BackupFileDto(String fileName, long sizeBytes, Instant modifiedAt) {}

    public record BackupDto(
            String schemaVersion,
            Instant generatedAt,
            List<PlaylistBackup> playlists,
            List<FavoriteBackup> favorites
    ) {
        public record PlaylistBackup(
                Long id,
                String name,
                String description,
                boolean isPublic,
                Long ownerId,
                List<EntryBackup> entries) {}

        public record EntryBackup(Long songId, Integer position) {}

        public record FavoriteBackup(Long userId, Long songId, Instant createdAt) {}
    }
}
