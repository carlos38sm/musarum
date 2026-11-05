package musarum.backend.admin;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import musarum.backend.album.AlbumRepository;
import musarum.backend.artist.ArtistRepository;
import musarum.backend.favorite.FavoriteRepository;
import musarum.backend.genre.GenreRepository;
import musarum.backend.playlist.PlaylistRepository;
import musarum.backend.reproduction.ReproductionRepository;
import musarum.backend.scanner.LibraryScanner;
import musarum.backend.scanner.ScannerStatus;
import musarum.backend.song.SongRepository;
import musarum.backend.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Calcula el snapshot de "Análisis del servidor". Todo on-demand: nada se
 * precalcula ni cachea — es un endpoint ocasional del OWNER, no vale la
 * pena desperdiciar memoria.
 *
 * La lectura de letras (synced vs plain) y el conteo de .lrc huérfanos
 * recorre {@code _lyrics/} en disco, por lo que puede tardar algo con
 * bibliotecas grandes. Mantenlo bajo tx readOnly para no bloquear.
 */
@Service
public class ServerStatsService {

    private static final Logger log = LoggerFactory.getLogger(ServerStatsService.class);

    // Pre-compilamos el patrón de línea sincronizada [mm:ss para no recompilar
    // una regex por cada .lrc que clasifiquemos — con 500+ canciones se nota.
    private static final Pattern SYNCED_LINE = Pattern.compile("(?s).*\\[\\d{1,2}:\\d{1,2}.*");

    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final GenreRepository genreRepository;
    private final PlaylistRepository playlistRepository;
    private final FavoriteRepository favoriteRepository;
    private final UserRepository userRepository;
    private final ReproductionRepository reproductionRepository;
    private final LibraryScanner libraryScanner;

    private final Path musicRoot;
    private final Path coversRoot;
    private final Path lyricsRoot;
    private final Path videosRoot;
    private final Path avatarsRoot;

    @PersistenceContext
    private EntityManager entityManager;

    ServerStatsService(SongRepository songRepository,
                       ArtistRepository artistRepository,
                       AlbumRepository albumRepository,
                       GenreRepository genreRepository,
                       PlaylistRepository playlistRepository,
                       FavoriteRepository favoriteRepository,
                       UserRepository userRepository,
                       ReproductionRepository reproductionRepository,
                       LibraryScanner libraryScanner,
                       @Value("${MUSARUM_MUSIC_PATH}") String musicPath,
                       @Value("${MUSARUM_COVERS_PATH:#{null}}") String coversPath,
                       @Value("${MUSARUM_VIDEOS_PATH:#{null}}") String videosPath,
                       @Value("${MUSARUM_AVATARS_PATH:#{null}}") String avatarsPath) {
        this.songRepository = songRepository;
        this.artistRepository = artistRepository;
        this.albumRepository = albumRepository;
        this.genreRepository = genreRepository;
        this.playlistRepository = playlistRepository;
        this.favoriteRepository = favoriteRepository;
        this.userRepository = userRepository;
        this.reproductionRepository = reproductionRepository;
        this.libraryScanner = libraryScanner;

        this.musicRoot = Path.of(musicPath);
        // Mismo criterio que CoverStorage/VideoStorage/AvatarStorage: si no
        // hay override en .env, usamos las carpetas hermanas por defecto.
        this.coversRoot  = resolveSubfolder(musicPath, coversPath,  "_covers");
        this.lyricsRoot  = musicRoot.resolve("_lyrics");
        this.videosRoot  = resolveSubfolder(musicPath, videosPath,  "_videos");
        this.avatarsRoot = resolveSubfolder(musicPath, avatarsPath, "_profilepictures");
    }

    private static Path resolveSubfolder(String music, String override, String subfolder) {
        return (override != null && !override.isBlank())
                ? Path.of(override)
                : Path.of(music).resolve(subfolder);
    }

    @Transactional(readOnly = true)
    public ServerStatsDto buildSnapshot() {
        return new ServerStatsDto(
                buildCatalog(),
                buildLyrics(),
                buildDisk(),
                buildEnrichment(),
                buildActivity(),
                buildTechnical()
        );
    }

    private ServerStatsDto.CatalogStats buildCatalog() {
        return new ServerStatsDto.CatalogStats(
                songRepository.count(),
                artistRepository.count(),
                albumRepository.count(),
                genreRepository.count(),
                playlistRepository.count(),
                favoriteRepository.count(),
                userRepository.count(),
                userRepository.countByBlocked(true)
        );
    }

    private ServerStatsDto.LyricsStats buildLyrics() {
        long total = songRepository.count();
        long with = songRepository.countWithLyrics();
        long without = Math.max(0, total - with);
        double pct = total == 0 ? 0.0 : (with * 100.0) / total;

        // Clasificar synced vs plain = leer la primera línea de cada .lrc.
        // Como mucho leemos "with" ficheros; cada read es un fopen de ~10KB.
        List<Long> idsWithLyrics = songRepository.findIdsWithLyrics();
        long synced = 0L;
        long plain = 0L;
        for (Long id : idsWithLyrics) {
            Path lrc = lyricsRoot.resolve(id + ".lrc");
            if (!Files.exists(lrc)) continue;
            try {
                // Nos basta ver si APARECE un timestamp [mm:ss en el fichero.
                // No parseamos entero — solo buscamos "[0" o "[1" al inicio
                // de alguna línea, que es como arrancan los LRC sincronizados.
                String head = Files.readString(lrc, StandardCharsets.UTF_8);
                if (head.contains("\n[") || head.startsWith("[")) {
                    // Más riguroso: comprobamos que haya al menos un patrón
                    // [N:NN] en el fichero para no contar como synced los que
                    // solo tienen metadata [ar:...]
                    if (SYNCED_LINE.matcher(head).matches()) synced++;
                    else plain++;
                } else {
                    plain++;
                }
            } catch (IOException e) {
                log.debug("No pude clasificar .lrc {}: {}", lrc, e.getMessage());
            }
        }

        return new ServerStatsDto.LyricsStats(with, without, pct, synced, plain);
    }

    private ServerStatsDto.DiskStats buildDisk() {
        long musicBytes = songRepository.sumFileSize();
        long coversBytes  = folderSize(coversRoot);
        long lyricsBytes  = folderSize(lyricsRoot);
        long videosBytes  = folderSize(videosRoot);
        long avatarsBytes = folderSize(avatarsRoot);
        long dbBytes = databaseSize();
        long free = 0L, total = 0L;
        try {
            FileStore store = Files.getFileStore(musicRoot);
            free = store.getUsableSpace();
            total = store.getTotalSpace();
        } catch (IOException e) {
            log.warn("No pude leer FileStore de {}: {}", musicRoot, e.getMessage());
        }
        return new ServerStatsDto.DiskStats(
                musicBytes, coversBytes, lyricsBytes, videosBytes, avatarsBytes,
                dbBytes, free, total
        );
    }

    private ServerStatsDto.EnrichmentStats buildEnrichment() {
        return new ServerStatsDto.EnrichmentStats(
                songRepository.countMissingCover(),
                songRepository.countMissingBpm(),
                songRepository.countMissingKey(),
                songRepository.countMissingReplayGain(),
                songRepository.countMissingMbid()
        );
    }

    private ServerStatsDto.ActivityStats buildActivity() {
        Instant now = Instant.now();
        Instant last7  = now.minus(7, ChronoUnit.DAYS);
        Instant last30 = now.minus(30, ChronoUnit.DAYS);
        long total = reproductionRepository.count();
        long last7Count  = reproductionRepository.countSince(last7);
        long last30Count = reproductionRepository.countSince(last30);
        long active30    = reproductionRepository.countDistinctUsersSince(last30);
        Instant lastPlayAt = reproductionRepository.findLastReproductionAt();

        ScannerStatus scanner = libraryScanner.currentStatus();
        Long lastScanAt = scanner != null ? scanner.startedAt() : null;
        String lastScanSummary = null;
        if (scanner != null && scanner.lastResult() != null) {
            var r = scanner.lastResult();
            StringBuilder sb = new StringBuilder();
            sb.append(r.imported()).append(" nuevas");
            if (r.duplicates() > 0) sb.append(" · ").append(r.duplicates()).append(" duplicadas");
            if (r.deleted() > 0) sb.append(" · ").append(r.deleted()).append(" borradas");
            lastScanSummary = sb.toString();
        }

        return new ServerStatsDto.ActivityStats(
                total, last7Count, last30Count, active30,
                lastPlayAt != null ? lastPlayAt.toEpochMilli() : null,
                lastScanAt,
                lastScanSummary
        );
    }

    private ServerStatsDto.TechnicalStats buildTechnical() {
        var rt = ManagementFactory.getRuntimeMXBean();
        Runtime jvm = Runtime.getRuntime();
        long used = jvm.totalMemory() - jvm.freeMemory();
        long orphans = countOrphanLyricFiles();
        return new ServerStatsDto.TechnicalStats(
                rt.getUptime(),
                used, jvm.totalMemory(), jvm.maxMemory(),
                orphans
        );
    }

    private long countOrphanLyricFiles() {
        return orphanLyricPaths().size();
    }

    /** Lista los `.lrc` cuyo nombre {id}.lrc no apunta a una canción existente. */
    public List<Path> orphanLyricPaths() {
        if (!Files.exists(lyricsRoot)) return List.of();
        // Antes usábamos findAll() y pelábamos getId() — cargaba 500+ entidades
        // completas para quedarnos solo con el id. findAllIds() proyecta directo.
        Set<Long> existing = new HashSet<>(songRepository.findAllIds());
        try (Stream<Path> files = Files.list(lyricsRoot)) {
            return files
                    .filter(p -> p.getFileName().toString().endsWith(".lrc"))
                    .filter(p -> {
                        String name = p.getFileName().toString();
                        String stem = name.substring(0, name.length() - 4);
                        try {
                            return !existing.contains(Long.parseLong(stem));
                        } catch (NumberFormatException e) { return false; }
                    })
                    .toList();
        } catch (IOException e) {
            log.warn("No pude listar _lyrics/: {}", e.getMessage());
            return List.of();
        }
    }

    /** Borra los `.lrc` huérfanos y devuelve cuántos se eliminaron. */
    public int deleteOrphanLyricFiles() {
        int deleted = 0;
        for (Path p : orphanLyricPaths()) {
            try { Files.deleteIfExists(p); deleted++; } catch (IOException e) {
                log.warn("No pude borrar {}: {}", p, e.getMessage());
            }
        }
        return deleted;
    }

    private long folderSize(Path root) {
        if (root == null || !Files.exists(root)) return 0L;
        try (Stream<Path> stream = Files.walk(root)) {
            return stream.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (IOException e) { return 0L; }
            }).sum();
        } catch (IOException e) {
            log.warn("No pude recorrer {}: {}", root, e.getMessage());
            return 0L;
        }
    }

    private long databaseSize() {
        try {
            Object result = entityManager
                    .createNativeQuery("SELECT pg_database_size(current_database())")
                    .getSingleResult();
            if (result instanceof Number n) return n.longValue();
            return 0L;
        } catch (Exception e) {
            log.warn("No pude leer pg_database_size: {}", e.getMessage());
            return 0L;
        }
    }
}
