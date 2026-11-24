package musarum.backend.scanner;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import musarum.backend.album.Album;
import musarum.backend.album.AlbumRepository;
import musarum.backend.album.AlbumService;
import musarum.backend.artist.Artist;
import musarum.backend.artist.ArtistRepository;
import musarum.backend.artist.ArtistService;
import musarum.backend.enrichment.EmendatiumAutoEnricher;
import musarum.backend.enrichment.LyricsWriter;
import musarum.backend.genre.Genre;
import musarum.backend.genre.GenreRepository;
import musarum.backend.genre.GenreService;
import musarum.backend.library.LibraryFolderService;
import musarum.backend.scanner.exception.ScanFailedException;
import musarum.backend.shared.CoverStorage;
import musarum.backend.shared.Normalizer;
import musarum.backend.shared.SmartCase;
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
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final GenreRepository genreRepository;
    private final TagReader tagReader;
    private final FileHasher fileHasher;
    private final CoverExtractor coverExtractor;
    private final CoverStorage coverStorage;
    private final EmendatiumAutoEnricher emendatiumEnricher;
    private final LyricsWriter lyricsWriter;
    private final LibraryFolderService libraryFolderService;
    // Self-reference para forzar que las llamadas internas a métodos
    // @Transactional (importSingleSongWithCatalog) pasen por el proxy de
    // Spring. Sin esto, invocar this.importSingleSongWithCatalog(...) desde
    // importNewSongs es self-invocation y la @Transactional se ignora.
    // @Lazy rompe el ciclo A → B → A al construir el bean.
    @Autowired @Lazy
    private LibraryScanner self;

    public LibraryScanner(@Value("${MUSARUM_MUSIC_PATH}") String musicPath,
                          SongService songService,
                          ArtistService artistService,
                          AlbumService albumService,
                          GenreService genreService,
                          ArtistRepository artistRepository,
                          AlbumRepository albumRepository,
                          GenreRepository genreRepository,
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
        this.artistRepository = artistRepository;
        this.albumRepository = albumRepository;
        this.genreRepository = genreRepository;
        this.tagReader = tagReader;
        this.fileHasher = fileHasher;
        this.coverExtractor = coverExtractor;
        this.coverStorage = coverStorage;
        this.emendatiumEnricher = emendatiumEnricher;
        this.lyricsWriter = lyricsWriter;
        this.libraryFolderService = libraryFolderService;
    }

    // Estado de progreso del último escaneo lanzado. Lectura por /scanner/status.
    // AtomicBoolean + compareAndSet garantiza que dos POST /scanner/import
    // concurrentes jamás disparen dos escaneos: el primero gana el CAS, el
    // segundo devuelve lastResult sin tocar nada. Antes era volatile boolean
    // y había una ventana de microsegundos entre el `if (running)` y el
    // `running = true` donde dos hilos podían pasar ambos el check.
    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile int total = 0;
    private final AtomicInteger processed = new AtomicInteger(0);
    private volatile String currentFile = null;
    private volatile Long startedAt = null;
    private volatile ImportResult lastResult = null;
    // "hashing" | "importing" | null — ver ScannerStatus.phase
    private volatile String phase = null;
    // Últimos ficheros que dieron error durante el scan. Buffer en memoria
    // de tamaño limitado (últimos 100) para que el OWNER pueda ver desde el
    // móvil qué está fallando sin tener que leer logs del contenedor.
    private final java.util.Deque<String> recentErrors = new java.util.ArrayDeque<>();
    private static final int MAX_RECENT_ERRORS = 100;

    /** Buffer de errores recientes (añade al final, recorta por el principio). */
    private void recordError(String message) {
        synchronized (recentErrors) {
            recentErrors.addLast(message);
            while (recentErrors.size() > MAX_RECENT_ERRORS) {
                recentErrors.removeFirst();
            }
        }
    }

    /** Snapshot ordenado (más antiguos primero). */
    public java.util.List<String> recentErrorsSnapshot() {
        synchronized (recentErrors) {
            return new java.util.ArrayList<>(recentErrors);
        }
    }

    public ScannerStatus currentStatus() {
        return new ScannerStatus(
                running.get(),
                total,
                processed.get(),
                currentFile,
                startedAt,
                lastResult,
                phase
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
        // compareAndSet gana solo si estaba false. Garantiza exclusión aunque
        // dos requests concurrentes lleguen en el mismo microsegundo.
        if (!running.compareAndSet(false, true)) {
            log.warn("Escaneo ya en curso, ignorando petición duplicada");
            return CompletableFuture.completedFuture(lastResult);
        }
        startedAt = System.currentTimeMillis();
        processed.set(0);
        currentFile = null;
        phase = "hashing";

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
            //
            // Esta fase es la LENTA con bibliotecas grandes (~1 MB de lectura
            // por fichero, 30-80ms SHA-256 cada uno). Paralelizamos con un
            // pool de 4 hilos — la CPU no es el cuello, es la latencia de I/O
            // de disco, así que N threads rinden bien incluso en HDD.
            Map<String, Path> diskHashToPath = new ConcurrentHashMap<>();
            AtomicInteger duplicateFiles = new AtomicInteger(0);
            AtomicInteger hashErrors = new AtomicInteger(0);
            int hashParallelism = Math.min(4, Math.max(2, Runtime.getRuntime().availableProcessors()));
            ExecutorService hashPool = Executors.newFixedThreadPool(hashParallelism, r -> {
                Thread t = new Thread(r, "scanner-hash");
                t.setDaemon(true);
                return t;
            });
            try {
                List<CompletableFuture<Void>> futures = diskFiles.stream()
                        .map(file -> CompletableFuture.runAsync(() -> {
                            // currentFile es volatile — con N hilos solo veremos
                            // el último; es solo para display, no lógica.
                            currentFile = file.getFileName().toString();
                            try {
                                String h = fileHasher.hash(file);
                                Path prev = diskHashToPath.putIfAbsent(h, file);
                                if (prev != null) {
                                    duplicateFiles.incrementAndGet();
                                    log.info("Duplicado por contenido: {} ↔ {}", file, prev);
                                }
                            } catch (Exception e) {
                                hashErrors.incrementAndGet();
                                log.warn("No se pudo hashear {}: {}", file, e.getMessage());
                            } finally {
                                processed.incrementAndGet();
                            }
                        }, hashPool))
                        .toList();
                CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
            } finally {
                hashPool.shutdown();
                try { hashPool.awaitTermination(5, TimeUnit.SECONDS); }
                catch (InterruptedException ignored) { Thread.currentThread().interrupt(); }
            }
            log.info("Hashes únicos: {}. Duplicados por contenido: {}. Errores de hash: {}",
                    diskHashToPath.size(), duplicateFiles.get(), hashErrors.get());

            Set<String> dbHashes = songService.findAllContentHashes();
            Set<String> dbPaths = songService.findAllAudioPaths();

            // Fase 2: importar. Reseteamos el contador para que el móvil
            // muestre "Importando 0/7037" y suba limpio desde cero.
            // Pre-cargamos el catálogo (artistas/álbumes/géneros) UNA vez
            // antes del bucle. Rompe el N+1 que hacía findOrCreate por cada
            // canción — ver ScannerCatalog para el motivo.
            phase = "importing";
            processed.set(0);
            total = diskHashToPath.size();

            ScannerCatalog catalog = new ScannerCatalog(
                    artistRepository, albumRepository, genreRepository,
                    artistService, albumService, genreService
            );
            log.info("ScannerCatalog listo. Cache inicial reduce N+1 en resolveArtist/Album/Genre.");

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
                        // Cada canción abre su propia transacción corta a
                        // través del proxy (self.importSingleSongWithCatalog).
                        // Llamar this.importSingleSongWithCatalog sería
                        // self-invocation y la @Transactional no aplicaría —
                        // esto nos protege contra futuros pasos que necesiten
                        // atomicidad global de "crear canción + aplicar
                        // enriquecimiento + cover + letra".
                        self.importSingleSongWithCatalog(path, hash, catalog);
                        imported++;
                        log.debug("Importada: {}", path);
                    }
                } catch (Exception e) {
                    failed++;
                    String reason = e.getClass().getSimpleName() + ": "
                            + (e.getMessage() != null ? e.getMessage() : "sin detalle");
                    recordError(path + " → " + reason);
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
                    duplicateFiles.get(), relocated, hashErrors.get()
            );
            lastResult = result;
            writeScanMarker();
            log.info("Escaneo terminado. En disco: {}, importadas: {}, fallidas: {}, "
                            + "borradas: {}, duplicadas: {}, reubicadas: {}, "
                            + "hashes refrescados: {}, errores hash: {}",
                    totalOnDisk, imported, failed, deleted,
                    duplicateFiles.get(), relocated, refreshed, hashErrors.get());

            return CompletableFuture.completedFuture(result);
        } finally {
            running.set(false);
            currentFile = null;
            phase = null;
        }
    }

    // ── Marker de "último escaneo" ──────────────────────────────────────
    // Fichero oculto en la raíz de MUSARUM_MUSIC_PATH con el epoch ms del
    // último escaneo completado. Lo usa LibraryWatcher.startWatching para
    // saltarse el escaneo inicial cuando el backend se reinicia poco
    // después (p. ej. durante desarrollo: 3 restarts en 10 min arrastraban
    // la app a trompicones porque cada uno relanzaba el full-scan sobre
    // el bind mount lento de Docker Desktop en Windows).

    private Path scanMarker() {
        return musicFolder.resolve(".musarum-last-scan");
    }

    private void writeScanMarker() {
        try {
            Files.writeString(scanMarker(), String.valueOf(System.currentTimeMillis()));
        } catch (IOException e) {
            log.warn("No se pudo escribir marker de scan: {}", e.getMessage());
        }
    }

    /**
     * Minutos transcurridos desde el último escaneo completo, o {@link
     * Long#MAX_VALUE} si no hay marker (primera instalación o lo borraron).
     */
    public long minutesSinceLastScan() {
        try {
            Path m = scanMarker();
            if (!Files.exists(m)) return Long.MAX_VALUE;
            long ts = Long.parseLong(Files.readString(m).trim());
            return (System.currentTimeMillis() - ts) / 60_000L;
        } catch (Exception e) {
            return Long.MAX_VALUE;
        }
    }

    /**
     * Versión usada por {@link LibraryWatcher} cuando se detecta un fichero
     * nuevo en caliente (crear-uno). No pasa por {@link ScannerCatalog}
     * porque solo importa UNA canción — construir el catálogo sería caro
     * para un solo insert. Mantiene la ruta simple: findOrCreate directo.
     */
    @Transactional
    public void importSingleSong(String audioPath, String contentHash) {
        importSingleSongInternal(audioPath, contentHash, null);
    }

    /**
     * Versión usada por el scanner en bulk. {@code catalog} tiene los mapas
     * id-por-clave pre-cargados; cada resolve* evita el SELECT redundante
     * contra Postgres salvo cuando hay que crear la entidad.
     */
    @Transactional
    public void importSingleSongWithCatalog(String audioPath, String contentHash,
                                            ScannerCatalog catalog) {
        importSingleSongInternal(audioPath, contentHash, catalog);
    }

    private void importSingleSongInternal(String audioPath, String contentHash,
                                          ScannerCatalog catalog) {
        AudioMetadata meta = tagReader.read(audioPath);
        Set<Artist> artists = resolveArtists(meta.artistName(), catalog);
        // SmartCase sobre tags ID3 en crudo: muchos ficheros vienen de
        // ripeos / descargas antiguas con "LA CANCIÓN DEL VERANO" o
        // "la cancion del verano" — capitalizamos al "estilo libro"
        // antes de que llegue a DB. Cuando Emendatium identifique
        // luego por AcoustID, sobreescribirá con el nombre canónico de
        // MusicBrainz (ya SmartCase-ado también) y la fila converge.
        String niceTitle = SmartCase.apply(meta.title());
        String niceAlbum = SmartCase.apply(meta.albumTitle());
        String niceGenre = SmartCase.apply(meta.genreName());

        Album album = null;
        if (niceAlbum != null && !artists.isEmpty()) {
            Artist primaryArtist = artists.iterator().next();
            album = (catalog != null)
                    ? catalog.resolveAlbum(niceAlbum, primaryArtist)
                    : albumService.findOrCreate(niceAlbum, primaryArtist);
        }

        Genre genre = null;
        if (niceGenre != null) {
            genre = (catalog != null)
                    ? catalog.resolveGenre(niceGenre)
                    : genreService.findOrCreate(niceGenre);
        }

        Song created = songService.create(
                audioPath,
                contentHash,
                niceTitle,
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
                // Antes era log.debug → invisible al OWNER; si el .mp3 tenía
                // frame USLT pero fallaba el write (disco lleno, permisos),
                // la canción se quedaba sin letra sin aviso. Sube a warn.
                log.warn("No se pudo guardar letra embebida de id={}: {}", songId, e.getMessage());
            }
        });
    }

    /** Extrae la portada embebida y la guarda si álbum/artista aún no la tienen. */
    private void extractAndStoreCovers(String audioPath, Album album, Set<Artist> artists) {
        // Importante: el APIC embebido en el audio es la portada del ÁLBUM,
        // NUNCA una foto del artista. Antes aquí también se asignaba al
        // artista como "imagen por defecto si no tiene", pero el resultado
        // era que casi todos los artistas de la biblioteca terminaban con
        // la carátula de un álbum como "cara". Solo álbumes a partir de nov-24
        // 2026. Las fotos reales de artista llegan por Emendatium (MB /
        // TheAudioDB) o por upload manual futuro. Si no hay ninguna, el
        // móvil pinta placeholder determinista por artistId.
        if (album == null) return;
        if (album.getCoverUrl() != null && !album.getCoverUrl().isBlank()) return;
        coverExtractor.extract(audioPath).ifPresent(cover -> {
            String url = coverStorage.saveAlbumCover(album.getId(), cover.bytes(), cover.mimeType());
            if (url != null) albumService.setCoverUrl(album.getId(), url);
        });
    }

    private Set<Artist> resolveArtists(String artistField, ScannerCatalog catalog) {
        Set<Artist> artists = new LinkedHashSet<>();
        if (artistField == null || artistField.isBlank()) return artists;
        // Dedupe por CLAVE normalizada antes de resolver. Antes se confiaba
        // en LinkedHashSet<Artist>#add para rechazar duplicados, pero
        // ScannerCatalog.resolveArtist devuelve un proxy nuevo en cada
        // llamada (getReferenceById no cachea), así que dos proxies con el
        // mismo id no son equals() y los dos acababan en el set. Cuando
        // Hibernate intentaba insertarlos en song_artists, Postgres tiraba
        // "duplicate key (song_id, artist_id)" y toda la canción fallaba.
        // Casos típicos: "Dimitri Vegas & Like Mike, Martin Garrix,
        // Dimitri Vegas - Tremor" (Dimitri sale dos veces), remixes donde
        // el mismo artista aparece como main + featuring, etc.
        Set<String> seenKeys = new HashSet<>();
        String[] names = artistField.split(ARTIST_SEPARATOR_REGEX, -1);
        for (String name : names) {
            String cleanName = name.replaceAll("[()\\[\\]]", "").trim();
            if (cleanName.isEmpty()) continue;
            // SmartCase ANTES del dedupe-por-key: así "LADY GAGA" y
            // "lady gaga" mapean al mismo nombre guardado ("Lady Gaga")
            // y además el findOrCreate por Normalizer.key sigue
            // deduplicando como siempre (es case-insensitive).
            String niceName = SmartCase.apply(cleanName);
            String key = Normalizer.key(niceName);
            if (!seenKeys.add(key)) continue; // ya resuelto en esta misma canción
            Artist a = (catalog != null)
                    ? catalog.resolveArtist(niceName)
                    : artistService.findOrCreate(niceName);
            artists.add(a);
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