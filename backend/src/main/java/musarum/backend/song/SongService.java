package musarum.backend.song;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import musarum.backend.album.Album;
import musarum.backend.artist.Artist;
import musarum.backend.enrichment.LrcLibClient;
import musarum.backend.enrichment.LyricsWriter;
import musarum.backend.favorite.FavoriteChecker;
import musarum.backend.genre.Genre;
import musarum.backend.song.dto.SongDetailsDto;
import musarum.backend.song.dto.SongSummaryDto;
import musarum.backend.song.exception.LyricsNotFoundException;
import musarum.backend.song.exception.SongNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SongService {
    private final SongRepository songRepository;
    private final SongMapper songMapper;
    private final FavoriteChecker favoriteChecker;
    private final LrcLibClient lrcLibClient;
    private final LyricsWriter lyricsWriter;
    private final LyricsLookupService lyricsLookupService;
    private final SongDeletionService songDeletionService;

    SongService(SongRepository songRepository,
                SongMapper songMapper,
                FavoriteChecker favoriteChecker,
                LrcLibClient lrcLibClient,
                LyricsWriter lyricsWriter,
                LyricsLookupService lyricsLookupService,
                SongDeletionService songDeletionService) {
        this.songRepository = songRepository;
        this.songMapper = songMapper;
        this.favoriteChecker = favoriteChecker;
        this.lrcLibClient = lrcLibClient;
        this.lyricsWriter = lyricsWriter;
        this.lyricsLookupService = lyricsLookupService;
        this.songDeletionService = songDeletionService;
    }

    @Transactional(readOnly = true)
    public SongDetailsDto findById(Long id, Long requesterId) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));
        boolean isFavorite = favoriteChecker.isFavorite(requesterId, id);
        return songMapper.toDetails(song, isFavorite);
    }

    @Transactional(readOnly = true)
    public Song findEntityById(Long id) {
        return songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));
    }

    /**
     * "Esta canción suena a…": encuentra canciones del catálogo con
     * tonalidad compatible en Camelot (misma, +1, −1, relativa) y BPM
     * dentro de ±8. Si la canción fuente no tiene BPM o tonalidad
     * (todavía no ha pasado Emendatium), devuelve lista vacía — el
     * móvil oculta la sección en ese caso.
     */
    @Transactional(readOnly = true)
    public List<SongSummaryDto> findSimilar(Long id, int size) {
        Song source = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));
        Integer bpm = source.getBpm();
        String key = source.getMusicalKey();
        if (bpm == null || bpm <= 0 || key == null || key.isBlank()) {
            return List.of();
        }
        List<String> compatibleKeys = compatibleKeyAliases(key);
        if (compatibleKeys.isEmpty()) return List.of();
        int capped = Math.max(1, Math.min(size, 20));
        int minBpm = Math.max(1, bpm - 8);
        int maxBpm = bpm + 8;
        List<Song> similar = songRepository.findSimilar(
                id, compatibleKeys, bpm, minBpm, maxBpm, capped);
        return similar.stream().map(songMapper::toSummary).toList();
    }

    /**
     * Devuelve TODAS las representaciones textuales de las 4 tonalidades
     * compatibles en Camelot (misma, −1, +1, relativa mayor/menor) para
     * una key que puede llegar en varios formatos: Camelot ("8A", "12B"),
     * nombre musical ("A minor", "D# major"), enarmónico ("Bb major",
     * "F#m") o abreviado ("Am", "C#"). Así la query nativa casa aunque
     * el catálogo mezcle formatos (Emendatium guarda "D minor",
     * jaudiotagger a veces "Dm", el user podría teclear "7A").
     */
    private static List<String> compatibleKeyAliases(String rawKey) {
        String camelot = toCamelot(rawKey);
        if (camelot == null) return List.of();
        char letter = camelot.charAt(camelot.length() - 1);
        int num;
        try {
            num = Integer.parseInt(camelot.substring(0, camelot.length() - 1));
        } catch (NumberFormatException e) {
            return List.of();
        }
        int prev = num == 1 ? 12 : num - 1;
        int next = num == 12 ? 1 : num + 1;
        char opposite = letter == 'A' ? 'B' : 'A';
        List<String> targets = List.of(
                num + "" + letter,
                prev + "" + letter,
                next + "" + letter,
                num + "" + opposite
        );
        // Para cada Camelot incluimos el propio código + todos los
        // nombres musicales equivalentes (ambas enarmónicas + abreviado).
        Set<String> aliases = new LinkedHashSet<>();
        for (String t : targets) {
            aliases.add(t);
            List<String> names = CAMELOT_TO_KEYS.get(t);
            if (names != null) aliases.addAll(names);
        }
        return new java.util.ArrayList<>(aliases);
    }

    /**
     * Normaliza una key (Camelot o nombre musical, con enarmónicos y
     * abreviaturas) al código Camelot canónico (p. ej. "A minor"→"8A",
     * "Bb major"→"6B", "F#m"→"11A"). Devuelve null si no reconoce el
     * formato.
     */
    private static String toCamelot(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) return null;
        String k = rawKey.trim().replaceAll("\\s+", " ");
        // Camelot directo "8A" / "12B"
        if (k.length() >= 2 && k.length() <= 3) {
            char last = Character.toUpperCase(k.charAt(k.length() - 1));
            if (last == 'A' || last == 'B') {
                try {
                    int n = Integer.parseInt(k.substring(0, k.length() - 1));
                    if (n >= 1 && n <= 12) return n + "" + last;
                } catch (NumberFormatException ignored) {}
            }
        }
        return KEY_TO_CAMELOT.get(k.toLowerCase());
    }

    private static final Map<String, String> KEY_TO_CAMELOT = buildKeyToCamelot();
    private static final Map<String, List<String>> CAMELOT_TO_KEYS = buildCamelotToKeys();

    private static Map<String, String> buildKeyToCamelot() {
        Map<String, String> m = new HashMap<>();
        // Menores (lado A) + enarmónicos + abreviaturas (ej. "Am", "G#m")
        addMinor(m, "1A",  "G#", "Ab");
        addMinor(m, "2A",  "D#", "Eb");
        addMinor(m, "3A",  "A#", "Bb");
        addMinor(m, "4A",  "F",  null);
        addMinor(m, "5A",  "C",  null);
        addMinor(m, "6A",  "G",  null);
        addMinor(m, "7A",  "D",  null);
        addMinor(m, "8A",  "A",  null);
        addMinor(m, "9A",  "E",  null);
        addMinor(m, "10A", "B",  null);
        addMinor(m, "11A", "F#", "Gb");
        addMinor(m, "12A", "C#", "Db");
        // Mayores (lado B)
        addMajor(m, "1B",  "B",  null);
        addMajor(m, "2B",  "F#", "Gb");
        addMajor(m, "3B",  "C#", "Db");
        addMajor(m, "4B",  "G#", "Ab");
        addMajor(m, "5B",  "D#", "Eb");
        addMajor(m, "6B",  "A#", "Bb");
        addMajor(m, "7B",  "F",  null);
        addMajor(m, "8B",  "C",  null);
        addMajor(m, "9B",  "G",  null);
        addMajor(m, "10B", "D",  null);
        addMajor(m, "11B", "A",  null);
        addMajor(m, "12B", "E",  null);
        return m;
    }

    private static void addMinor(Map<String, String> m, String camelot, String note, String alt) {
        m.put((note + " minor").toLowerCase(), camelot);
        m.put((note + "m").toLowerCase(), camelot);
        m.put((note + " min").toLowerCase(), camelot);
        if (alt != null) {
            m.put((alt + " minor").toLowerCase(), camelot);
            m.put((alt + "m").toLowerCase(), camelot);
            m.put((alt + " min").toLowerCase(), camelot);
        }
    }

    private static void addMajor(Map<String, String> m, String camelot, String note, String alt) {
        m.put((note + " major").toLowerCase(), camelot);
        m.put((note + " maj").toLowerCase(), camelot);
        m.put(note.toLowerCase(), camelot);
        if (alt != null) {
            m.put((alt + " major").toLowerCase(), camelot);
            m.put((alt + " maj").toLowerCase(), camelot);
            m.put(alt.toLowerCase(), camelot);
        }
    }

    private static Map<String, List<String>> buildCamelotToKeys() {
        Map<String, List<String>> m = new HashMap<>();
        m.put("1A",  List.of("G# minor", "Ab minor", "G#m", "Abm"));
        m.put("2A",  List.of("D# minor", "Eb minor", "D#m", "Ebm"));
        m.put("3A",  List.of("A# minor", "Bb minor", "A#m", "Bbm"));
        m.put("4A",  List.of("F minor",  "Fm"));
        m.put("5A",  List.of("C minor",  "Cm"));
        m.put("6A",  List.of("G minor",  "Gm"));
        m.put("7A",  List.of("D minor",  "Dm"));
        m.put("8A",  List.of("A minor",  "Am"));
        m.put("9A",  List.of("E minor",  "Em"));
        m.put("10A", List.of("B minor",  "Bm"));
        m.put("11A", List.of("F# minor", "Gb minor", "F#m", "Gbm"));
        m.put("12A", List.of("C# minor", "Db minor", "C#m", "Dbm"));
        m.put("1B",  List.of("B major",  "B"));
        m.put("2B",  List.of("F# major", "Gb major", "F#", "Gb"));
        m.put("3B",  List.of("C# major", "Db major", "C#", "Db"));
        m.put("4B",  List.of("G# major", "Ab major", "G#", "Ab"));
        m.put("5B",  List.of("D# major", "Eb major", "D#", "Eb"));
        m.put("6B",  List.of("A# major", "Bb major", "A#", "Bb"));
        m.put("7B",  List.of("F major",  "F"));
        m.put("8B",  List.of("C major",  "C"));
        m.put("9B",  List.of("G major",  "G"));
        m.put("10B", List.of("D major",  "D"));
        m.put("11B", List.of("A major",  "A"));
        m.put("12B", List.of("E major",  "E"));
        return m;
    }

    @Transactional(readOnly = true)
    public Optional<Song> findEntityByAudioPath(String audioPath) {
        return songRepository.findByAudioPath(audioPath);
    }

    @Transactional(readOnly = true)
    public Page<SongSummaryDto> findByAlbum(Long albumId, Pageable pageable) {
        return songRepository.findByAlbumIdOrderByTrackNumberAsc(albumId, pageable)
                .map(songMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<SongSummaryDto> findByGenre(Long genreId, Pageable pageable) {
        return songRepository.findByGenreId(genreId, pageable)
                .map(songMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<SongSummaryDto> findByArtist(Long artistId, Pageable pageable) {
        return songRepository.findByArtistId(artistId, pageable)
                .map(songMapper::toSummary);
    }

    /**
     * Carga varias canciones en el orden pedido (preservando duplicados hay
     * que ponerlos en el input). Útil para rankings donde el orden lo manda
     * la fuente externa (p. ej. "top de la semana").
     */
    @Transactional(readOnly = true)
    public List<SongSummaryDto> findByIdsOrdered(List<Long> ids) {
        if (ids == null || ids.isEmpty()) return List.of();
        java.util.Map<Long, Song> byId = songRepository.findAllById(ids).stream()
                .collect(java.util.stream.Collectors.toMap(Song::getId, s -> s));
        return ids.stream()
                .map(byId::get)
                .filter(java.util.Objects::nonNull)
                .map(songMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    /** Total de canciones del catálogo. Barato (COUNT(*)). */
    public long countAll() {
        return songRepository.count();
    }

    /** Duración total del catálogo en ms (SUM sobre todas las canciones). */
    @Transactional(readOnly = true)
    public long totalDurationMs() {
        return songRepository.sumDurationMsAll();
    }

    @Transactional(readOnly = true)
    public Page<SongSummaryDto> findAll(Pageable pageable) {
        // Ignoramos el sort del Pageable: el orden "humano" (letras primero,
        // luego números/símbolos/no-latinos) lo fija la query nativa, y tiene
        // que coincidir con el que usan countBefore y letterOffsets para que
        // el jumpbar y el FAB "ir a la canción que suena" cuadren.
        long total = songRepository.count();
        List<SongSummaryDto> content = songRepository
                .findPageOrdered(pageable.getPageSize(), (int) pageable.getOffset())
                .stream()
                .map(songMapper::toSummary)
                .toList();
        return new org.springframework.data.domain.PageImpl<>(content, pageable, total);
    }

    /**
     * Marca la canción con el status del último intento de enriquecimiento
     * Emendatium. Silenciosamente no-op si el id ya no existe (la canción
     * pudo ser borrada entre el enrichAsync y aquí). Tx corta propia porque
     * se llama desde {@code EmendatiumAutoEnricher} que no tiene tx externa.
     */
    @Transactional
    public void markEnrichment(Long songId, EnrichStatus status) {
        songRepository.findById(songId).ifPresent(s -> s.markEnrichment(status));
    }

    @Transactional(readOnly = true)
    public List<SongSummaryDto> findAllShuffled() {
        return songRepository.findAllShuffled().stream()
                .map(songMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<SongSummaryDto> findAllOrdered() {
        return songRepository.findAllOrdered().stream()
                .map(songMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Page<SongSummaryDto> search(String query, Pageable pageable) {
        return songRepository.findByTitleContainingIgnoreCase(query, pageable)
        		.map(songMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public String getAudioPath(Long id) {
        Song song = songRepository.findById(id)
        		.orElseThrow(() -> new SongNotFoundException(id));
        return song.getAudioPath();
    }

    /**
     * Letras de una canción. Orden de intentos:
     *   1. Fichero local (.lrc) si está guardado.
     *   2. LrcLib en caliente (sin esperar a Emendatium async). Si trae
     *      algo, se cachea en disco y se asigna como lyricsPath.
     *   3. 404 si ninguno dio nada.
     */
    /**
     * Devuelve la letra de la canción. Delega en {@link LyricsLookupService}
     * para que la llamada HTTP a LrcLib NO se haga dentro de una tx abierta
     * (bloquearía una conexión del pool esperando a un servicio externo).
     */
    public String getLyrics(Long id) {
        return lyricsLookupService.getLyrics(id);
    }

    public String refreshLyrics(Long id) {
        return lyricsLookupService.refreshLyrics(id);
    }

    @Transactional(readOnly = true)
    public Set<String> findAllAudioPaths() {
        return songRepository.findAllAudioPaths();
    }

    @Transactional(readOnly = true)
    public int countByAudioPathPrefix(String prefix) {
        return (int) songRepository.countByAudioPathPrefix(prefix);
    }

    /**
     * Posición 0-based de una canción en la lista paginada por defecto
     * (title ASC + id como desempate). Devuelve empty si la canción no
     * existe. Se usa para "ir a la canción que suena" en el móvil.
     */
    @Transactional(readOnly = true)
    public Optional<Long> findPosition(Long songId) {
        return songRepository.findById(songId)
                .map(song -> songRepository.countBefore(song.getTitle(), songId));
    }

    /**
     * Mapa {letra → posición absoluta} para que el móvil haga jump por letra
     * en una lista paginada. Carga solo títulos (proyección), así no instancia
     + 10k entidades para un recorrido lineal corto. Para 10k títulos son
     * ~ milisegundos.
     */
    @Transactional(readOnly = true)
    public Map<String, Integer> letterOffsets() {
        List<String> titles = songRepository.findAllTitlesForIndex();
        Map<String, Integer> offsets = new LinkedHashMap<>();
        for (int i = 0; i < titles.size(); i++) {
            String letter = firstLetter(titles.get(i));
            // Guardamos SOLO la primera aparición de cada letra.
            offsets.putIfAbsent(letter, i);
        }
        return offsets;
    }

    private String firstLetter(String title) {
        if (title == null || title.isBlank()) return "#";
        char c = Character.toUpperCase(title.trim().charAt(0));
        return Character.isLetter(c) ? String.valueOf(c) : "#";
    }

    /**
     * Cola "radio" a partir de una canción semilla. Elige por afinidad en
     * este orden de peso:
     *   1. Mismo género (si existe)   → 60% de los candidatos
     *   2. Mismos artistas             → 30%
     *   3. Resto aleatorio             → 10% (para que no quede incestuoso)
     *
     * Luego los baraja y recorta a {@code size}. La semilla NUNCA aparece
     * en el resultado para no sonar dos veces seguidas. No garantiza orden
     * ni "mix por BPM"; es un filtro de proximidad simple pero efectivo
     * para no saltar de ópera a reggaetón.
     */
    @Transactional(readOnly = true)
    public List<SongSummaryDto> radioFrom(Long seedSongId, int size) {
        Song seed = songRepository.findById(seedSongId)
                .orElseThrow(() -> new SongNotFoundException(seedSongId));
        int capped = Math.max(10, Math.min(size, 100));

        java.util.LinkedHashSet<Song> pool = new java.util.LinkedHashSet<>();

        // 1. Mismo género: traemos ~60% y mezclamos.
        Genre genre = seed.getGenre();
        if (genre != null) {
            int quota = (int) Math.ceil(capped * 0.6);
            List<Song> sameGenre = new java.util.ArrayList<>(songRepository
                    .findByGenreId(genre.getId(),
                            org.springframework.data.domain.PageRequest.of(0, quota * 3))
                    .getContent());
            java.util.Collections.shuffle(sameGenre);
            sameGenre.stream().limit(quota).forEach(pool::add);
        }

        // 2. Mismos artistas: traemos ~30% repartido entre todos los artistas.
        if (!seed.getArtists().isEmpty()) {
            int quotaPerArtist = Math.max(1, (int) Math.ceil(capped * 0.3 / seed.getArtists().size()));
            seed.getArtists().forEach(a -> {
                List<Song> fromArtist = new java.util.ArrayList<>(songRepository
                        .findByArtistId(a.getId(),
                                org.springframework.data.domain.PageRequest.of(0, quotaPerArtist * 3))
                        .getContent());
                java.util.Collections.shuffle(fromArtist);
                fromArtist.stream().limit(quotaPerArtist).forEach(pool::add);
            });
        }

        // 3. Si aún faltan, aleatorio de todo el catálogo.
        if (pool.size() < capped) {
            List<Song> random = new java.util.ArrayList<>(songRepository.findAllShuffled());
            for (Song s : random) {
                if (pool.size() >= capped) break;
                pool.add(s);
            }
        }

        // La semilla fuera, el resto barajado.
        List<Song> result = new java.util.ArrayList<>(pool);
        result.removeIf(s -> s.getId().equals(seedSongId));
        java.util.Collections.shuffle(result);
        return result.stream()
                .limit(capped)
                .map(songMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public Set<String> findAllContentHashes() {
        return songRepository.findAllContentHashes();
    }

    @Transactional
    public boolean relocateByHash(String contentHash, String newAudioPath) {
        return songRepository.findByContentHash(contentHash)
                .map(song -> {
                    if (!song.getAudioPath().equals(newAudioPath)) {
                        song.updateAudioPath(newAudioPath);
                        return true;
                    }
                    return false;
                })
                .orElse(false);
    }

    /**
     * Setea la ruta nueva de un fichero en DB. Lo invoca el enriquecimiento
     * cuando Emendatium renombra el .mp3 "cancion-spotifydownload.com.mp3"
     * a "Artist - Title.mp3". Sin este update, Musarum intentaría servir el
     * fichero por la ruta vieja y respondería 404 hasta el próximo rescan.
     */
    @Transactional
    public void updateAudioPath(Long songId, String newAudioPath) {
        songRepository.findById(songId).ifPresent(song -> song.updateAudioPath(newAudioPath));
    }

    /**
     * Si existe una canción con ese audio_path en la DB, actualiza su
     * content_hash al nuevo valor y devuelve true. Útil cuando cambiamos
     * el algoritmo de hash: la fila ya existe (misma ruta), solo hay que
     * refrescar la firma. No borra ni duplica nada.
     */
    @Transactional
    public boolean refreshHashByAudioPath(String audioPath, String newContentHash) {
        return songRepository.findByAudioPath(audioPath)
                .map(song -> {
                    song.updateContentHash(newContentHash);
                    return true;
                })
                .orElse(false);
    }

    /**
     * Borrado masivo usado por el scanner cuando detecta canciones que ya
     * NO están en disco. Antes hacía {@code DELETE FROM songs WHERE
     * content_hash IN (...)} directo, lo que violaba la FK
     * {@code playlist_entries_song_fk} si alguna de esas canciones seguía
     * referenciada (síntoma: "Fallo en el escaneo inicial:
     * DataIntegrityViolationException... playlist_entries"). Ahora
     * delegamos en {@link SongDeletionService#deleteCompletely} que limpia
     * favoritos, playlist_entries, reproducciones, fichero y huérfanos
     * en el orden correcto (álbum antes que artista, ver el fix de ayer).
     */
    /**
     * Aplica SOLO los campos escalares del enrichment (bpm, key, replayGain,
     * lyricsPath, mbId, isrc) en una tx corta que NO toca la colección
     * {@code song.artists}. Pensado para que un fallo posterior al aplicar
     * artist/album enrichment (ej. TransientPropertyValueException porque el
     * join table song_artists apunta a un Artist que ya no existe) no pierda
     * los escalares, que son lo más valioso para el usuario (BPM y tonalidad
     * visibles en el panel Musicalidad del móvil).
     */
    @Transactional
    public void applyScalarEnrichment(Long songId, Integer bpm, String musicalKey,
                                      Double replayGain, String lyricsPath,
                                      String mbId, String isrc) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new SongNotFoundException(songId));
        song.applyEnrichment(bpm, musicalKey, replayGain, lyricsPath, mbId, isrc);
    }

    @Transactional
    public int deleteByContentHashes(Set<String> hashes) {
        if (hashes.isEmpty()) return 0;
        int deleted = 0;
        for (String hash : hashes) {
            Long id = songRepository.findByContentHash(hash).map(Song::getId).orElse(null);
            if (id == null) continue;
            try {
                songDeletionService.deleteCompletely(id);
                deleted++;
            } catch (Exception e) {
                // No rompemos el bulk entero por una canción atípica: la
                // registramos y seguimos con las demás.
                org.slf4j.LoggerFactory.getLogger(SongService.class)
                        .warn("Fallo borrando canción id={} (hash={}): {}", id, hash, e.getMessage());
            }
        }
        return deleted;
    }

    /** Variante de una sola canción. Mismo camino que el bulk. */
    @Transactional
    public boolean deleteByContentHash(String contentHash) {
        return songRepository.findByContentHash(contentHash)
                .map(song -> {
                    songDeletionService.deleteCompletely(song.getId());
                    return true;
                })
                .orElse(false);
    }


    @Transactional
    public Song create(String audioPath, String contentHash, String title, Integer durationMs,
                       Integer trackNumber, Set<Artist> artists,
                       Album album, Genre genre,
                       Integer year, Integer bitrate, Integer sampleRate, Integer channels,
                       String format, Long fileSize, Integer discNumber, String composer) {

        Song song = Song.builder()
                .audioPath(audioPath)
                .contentHash(contentHash)
                .title(title != null ? title : extractTitleFromPath(audioPath))
                .durationMs(durationMs)
                .trackNumber(trackNumber)
                .artists(artists)
                .album(album)
                .genre(genre)
                .year(year)
                .bitrate(bitrate)
                .sampleRate(sampleRate)
                .channels(channels)
                .format(format)
                .fileSize(fileSize)
                .discNumber(discNumber)
                .composer(composer)
                .build();

        return songRepository.save(song);
    }

    private String extractTitleFromPath(String audioPath) {
        String fileName = audioPath.substring(audioPath.lastIndexOf("/") + 1);
        int dotIndex = fileName.lastIndexOf(".");
        return dotIndex > 0 ? fileName.substring(0, dotIndex) : fileName;
    }

    @Transactional
    public void setLyricsPath(Long songId, String path) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new musarum.backend.song.exception.SongNotFoundException(songId));
        song.setLyricsPath(path);
    }
}