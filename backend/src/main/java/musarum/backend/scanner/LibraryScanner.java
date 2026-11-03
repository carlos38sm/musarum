package musarum.backend.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import musarum.backend.album.Album;
import musarum.backend.album.AlbumService;
import musarum.backend.artist.Artist;
import musarum.backend.artist.ArtistService;
import musarum.backend.enrichment.EmendatiumAutoEnricher;
import musarum.backend.enrichment.LyricsWriter;
import musarum.backend.genre.Genre;
import musarum.backend.genre.GenreService;
import musarum.backend.library.LibraryFolderService;
import musarum.backend.scanner.exception.ScanFailedException;
import musarum.backend.shared.CoverStorage;
import musarum.backend.song.Song;
import musarum.backend.song.SongService;

@Service
public class LibraryScanner {

    private static final Logger log = LoggerFactory.getLogger(LibraryScanner.class);
    private static final String ARTIST_SEPARATOR_REGEX =
            "(?i)\\s*[,/;&+]\\s*|\\s+(?:feat\\.?|ft\\.?|featuring|with|vs\\.?|x)\\s+";

    private final Path musicFolder;
    private final SongService songService;
    private final ArtistService artistService;
    private final AlbumService albumService;
    private final GenreService genreService;
    private final TagReader tagReader;
    private final FileHasher fileHasher;
    private final CoverExtractor coverExtractor;
    private final CoverStorage coverStorage;
    private final EmendatiumAutoEnricher emendatiumEnricher;
    private final LyricsWriter lyricsWriter;
    private final LibraryFolderService libraryFolderService;

    public LibraryScanner(@Value("${MUSARUM_MUSIC_PATH}") String musicPath,
                          SongService songService,
                          ArtistService artistService,
                          AlbumService albumService,
                          GenreService genreService,
                          TagReader tagReader,
                          FileHasher fileHasher,
                          CoverExtractor coverExtractor,
                          CoverStorage coverStorage,
                          EmendatiumAutoEnricher emendatiumEnricher,
                          LyricsWriter lyricsWriter,
                          LibraryFolderService libraryFolderService) {
        this.musicFolder = Path.of(musicPath);
        this.songService = songService;
        this.artistService = artistService;
        this.albumService = albumService;
        this.genreService = genreService;
        this.tagReader = tagReader;
        this.fileHasher = fileHasher;
        this.coverExtractor = coverExtractor;
        this.coverStorage = coverStorage;
        this.emendatiumEnricher = emendatiumEnricher;
        this.lyricsWriter = lyricsWriter;
        this.libraryFolderService = libraryFolderService;
    }

    // Estado de progreso del último escaneo lanzado. Lectura por /scanner/status.
    // Volatile porque lo lee el hilo de la petición HTTP y lo escribe el pool
    // scannerExecutor. AtomicInteger para el contador de progreso (incrementos
    // concurrentes seguros aunque hoy solo haya un hilo).
    private volatile boolean running = false;
    private volatile int total = 0;
    private final AtomicInteger processed = new AtomicInteger(0);
    private volatile String currentFile = null;
    private volatile Long startedAt = null;
    private volatile ImportResult lastResult = null;

    public ScannerStatus currentStatus() {
        return new ScannerStatus(
                running,
                total,
                processed.get(),
                currentFile,
                startedAt,
                lastResult
        );
    }

    /**
     * Importa a la biblioteca todos los MP3/FLAC/… nuevos que haya en disco.
     *
     * Diferencias clave con la versión @Transactional externa:
     *  - El método externo NO abre una transacción. Si lo hiciera, Hibernate
     *    acumularía las 7000 entidades en la 1st-level cache hasta el commit
     *    y la memoria explotaría.
     *  - Cada canción se importa en su propia transacción vía importSingleSong
     *    (@Transactional, REQUIRES_NEW implícito al no haber tx padre). Esto
     *    libera la session tras cada canción.
     *  - Mantiene un contador de progreso leíble por /scanner/status para que
     *    el móvil pueda pintar "Escaneando 1200/7000...".
     */
    @Async("scannerExecutor")
    public CompletableFuture<ImportResult> importNewSongs() {
        if (running) {
            log.warn("Escaneo ya en curso, ignorando petición duplicada");
            return CompletableFuture.completedFuture(lastResult);
        }
        running = true;
        startedAt = System.currentTimeMillis();
        processed.set(0);
        currentFile = null;

        try {
            log.info("Escaneo iniciado en hilo: {}", Thread.currentThread().getName());

            List<Path> diskFiles = findAudioFiles();
            int totalOnDisk = diskFiles.size();
            total = totalOnDisk;
            log.info("Archivos de audio localizados en disco: {}", totalOnDisk);

            // Agrupamos por hash de contenido. Si dos archivos tienen bytes
            // idénticos, los contamos como duplicados pero solo importamos
            // uno (UNIQUE sobre content_hash). Explica la típica brecha
            // "la carpeta tiene N y Musarum muestra <N".
            Map<String, Path> diskHashToPath = new HashMap<>();
            int duplicateFiles = 0;
            int hashErrors = 0;
            for (Path file : diskFiles) {
                try {
                    String h = fileHasher.hash(file);
                    Path prev = diskHashToPath.put(h, file);
                    if (prev != null) {
                        duplicateFiles++;
                        log.info("Duplicado por contenido: {} ↔ {}", file, prev);
                    }
                } catch (Exception e) {
                    hashErrors++;
                    log.warn("No se pudo hashear {}: {}", file, e.getMessage());
                }
            }
            log.info("Hashes únicos: {}. Duplicados por contenido: {}. Errores de hash: {}",
                    diskHashToPath.size(), duplicateFiles, hashErrors);

            Set<String> dbHashes = songService.findAllContentHashes();
            Set<String> dbPaths = songService.findAllAudioPaths();

            int imported = 0;
            int failed = 0;
            int relocated = 0;
            int refreshed = 0;

            for (Map.Entry<String, Path> entry : diskHashToPath.entrySet()) {
                String hash = entry.getKey();
                Path file = entry.getValue();
                String path = normalize(file);
                currentFile = file.getFileName().toString();

                try {
                    if (dbHashes.contains(hash)) {
                        // El contenido ya está en DB con un hash igual al de
                        // disco — solo puede haber cambiado la ruta.
                        boolean updated = songService.relocateByHash(hash, path);
                        if (updated) relocated++;
                    } else if (dbPaths.contains(path)) {
                        // Mismo audio_path, hash distinto: la fila existe
                        // pero su firma está "vieja" (típico tras cambiar el
                        // algoritmo de hash). Refrescamos el hash en la fila
                        // existente en vez de insertar una duplicada. La
                        // UNIQUE sobre audio_path (V2) también lo impediría.
                        boolean updated = songService.refreshHashByAudioPath(path, hash);
                        if (updated) refreshed++;
                    } else {
                        // Cada canción abre su propia transacción corta.
                        importSingleSong(path, hash);
                        imported++;
                        log.debug("Importada: {}", path);
                    }
                } catch (Exception e) {
                    failed++;
                    log.warn("Error importando {}: {}", path, e.getMessage());
                } finally {
                    processed.incrementAndGet();
                }
            }

            Set<String> removedHashes = new HashSet<>(dbHashes);
            removedHashes.removeAll(diskHashToPath.keySet());
            // Cuidado: tras refreshear hashes algunos de los "removedHashes"
            // ya no existen en DB; deleteByContentHashes es idempotente.
            int deleted = songService.deleteByContentHashes(removedHashes);

            ImportResult result = new ImportResult(
                    totalOnDisk, imported, failed, deleted,
                    duplicateFiles, relocated, hashErrors
            );
            lastResult = result;
            log.info("Escaneo terminado. En disco: {}, importadas: {}, fallidas: {}, "
                            + "borradas: {}, duplicadas: {}, reubicadas: {}, "
                            + "hashes refrescados: {}, errores hash: {}",
                    totalOnDisk, imported, failed, deleted,
                    duplicateFiles, relocated, refreshed, hashErrors);

            return CompletableFuture.completedFuture(result);
        } finally {
            running = false;
            currentFile = null;
        }
    }

    @Transactional
    public void importSingleSong(String audioPath, String contentHash) {
        AudioMetadata meta = tagReader.read(audioPath);
        Set<Artist> artists = resolveArtists(meta.artistName());
        Album album = null;
        if (meta.albumTitle() != null && !artists.isEmpty()) {
            Artist primaryArtist = artists.iterator().next();
            album = albumService.findOrCreate(meta.albumTitle(), primaryArtist);
        }

        Genre genre = null;
        if (meta.genreName() != null) genre = genreService.findOrCreate(meta.genreName());

        Song created = songService.create(
                audioPath,
                contentHash,
                meta.title(),
                meta.durationMs(),
                meta.trackNumber(),
                artists,
                album,
                genre,
                meta.year(),
                meta.bitrate(),
                meta.sampleRate(),
                meta.channels(),
                meta.format(),
                meta.fileSize(),
                meta.discNumber(),
                meta.composer()
        );

        // Portada embebida (APIC): aplicamos solo si ni álbum ni artista la tienen aún
        extractAndStoreCovers(audioPath, album, artists);

        // Letra embebida (USLT): el MP3 original suele traer una, aunque LrcLib no la tenga
        if (created != null) {
            extractAndStoreLyrics(audioPath, created.getId());
        }

        // Enriquecimiento asíncrono con Emendatium (si está configurado)
        if (created != null) {
            emendatiumEnricher.enrichAsync(created.getId(), audioPath);
        }
    }

    /** Si el audio trae letra embebida (USLT/LYRICS), la guarda como .lrc local. */
    private void extractAndStoreLyrics(String audioPath, Long songId) {
        tagReader.extractEmbeddedLyrics(audioPath).ifPresent(lyrics -> {
            try {
                String path = lyricsWriter.write(songId, lyrics);
                songService.setLyricsPath(songId, path);
                log.info("  📝 Letra embebida extraída para id={}", songId);
            } catch (Exception e) {
                log.debug("No se pudo guardar letra embebida de id={}: {}", songId, e.getMessage());
            }
        });
    }

    /** Extrae la portada embebida y la guarda si álbum/artista aún no la tienen. */
    private void extractAndStoreCovers(String audioPath, Album album, Set<Artist> artists) {
        boolean albumNeedsCover = album != null && (album.getCoverUrl() == null || album.getCoverUrl().isBlank());
        Artist primaryArtist = artists.isEmpty() ? null : artists.iterator().next();
        boolean artistNeedsImage = primaryArtist != null
                && (primaryArtist.getImageUrl() == null || primaryArtist.getImageUrl().isBlank());

        if (!albumNeedsCover && !artistNeedsImage) return;

        coverExtractor.extract(audioPath).ifPresent(cover -> {
            if (albumNeedsCover) {
                String url = coverStorage.saveAlbumCover(album.getId(), cover.bytes(), cover.mimeType());
                if (url != null) albumService.setCoverUrl(album.getId(), url);
            }
            if (artistNeedsImage) {
                String url = coverStorage.saveArtistImage(primaryArtist.getId(), cover.bytes(), cover.mimeType());
                if (url != null) artistService.setImageUrl(primaryArtist.getId(), url);
            }
        });
    }

    private Set<Artist> resolveArtists(String artistField) {
        Set<Artist> artists = new LinkedHashSet<>();
        if (artistField == null || artistField.isBlank()) return artists;
        String[] names = artistField.split(ARTIST_SEPARATOR_REGEX, -1);
        for (String name : names) {
            String cleanName = name.replaceAll("[()\\[\\]]", "").trim();
            if (!cleanName.isEmpty()) artists.add(artistService.findOrCreate(cleanName));
        }

        return artists;
    }

    /**
     * Lista los audios que el scanner debe considerar en este pase.
     *
     * Recorre cada una de las carpetas marcadas como activas en DB (ver
     * {@link LibraryFolderService#activePaths()}). Si no hay ninguna activa
     * — p. ej. instalación nueva antes de que el OwnerBootstrap siembre la
     * carpeta paraguas — devuelve lista vacía en vez de petar. Carpetas
     * reservadas de Musarum ({@code _covers}, {@code _lyrics}, {@code _videos})
     * se descartan aunque la raíz esté activa.
     */
    public List<Path> findAudioFiles() {
        List<Path> active = libraryFolderService.activePaths();
        if (active.isEmpty()) {
            log.info("No hay carpetas activas; scanner no recorre nada en este pase");
            return List.of();
        }
        List<Path> all = new java.util.ArrayList<>();
        for (Path root : active) {
            if (!Files.isDirectory(root)) {
                log.warn("Carpeta activa inexistente en disco: {}", root);
                continue;
            }
            try (Stream<Path> paths = Files.walk(root)) {
                paths.filter(Files::isRegularFile)
                     .filter(this::isAudioFile)
                     .filter(this::isNotReservedDir)
                     .forEach(all::add);
            } catch (IOException e) {
                throw new ScanFailedException(root, e);
            }
        }
        return all;
    }

    /** Descarta rutas dentro de {@code _covers}, {@code _lyrics}, {@code _videos}. */
    private boolean isNotReservedDir(Path file) {
        for (Path p = file.getParent(); p != null; p = p.getParent()) {
            Path name = p.getFileName();
            if (name == null) continue;
            String n = name.toString();
            if (n.equals("_covers") || n.equals("_lyrics") || n.equals("_videos")) return false;
        }
        return true;
    }

    public String normalize(Path path) {
        return path.toString().replace("\\", "/");
    }

    public boolean isAudioFile(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        return name.endsWith(".mp3")
                || name.endsWith(".flac")
                || name.endsWith(".m4a")
                || name.endsWith(".ogg")
                || name.endsWith(".wav")
                || name.endsWith(".opus")
                || name.endsWith(".aac")
                || name.endsWith(".wma")
                || name.endsWith(".aiff")
                || name.endsWith(".aif");
    }
    
}