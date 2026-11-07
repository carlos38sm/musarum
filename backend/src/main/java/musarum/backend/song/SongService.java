package musarum.backend.song;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
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

    SongService(SongRepository songRepository,
                SongMapper songMapper,
                FavoriteChecker favoriteChecker,
                LrcLibClient lrcLibClient,
                LyricsWriter lyricsWriter,
                LyricsLookupService lyricsLookupService) {
        this.songRepository = songRepository;
        this.songMapper = songMapper;
        this.favoriteChecker = favoriteChecker;
        this.lrcLibClient = lrcLibClient;
        this.lyricsWriter = lyricsWriter;
        this.lyricsLookupService = lyricsLookupService;
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

    @Transactional(readOnly = true)
    public List<SongSummaryDto> findAllShuffled() {
        return songRepository.findAllShuffled().stream()
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

    @Transactional
    public int deleteByContentHashes(Set<String> hashes) {
        if (hashes.isEmpty()) return 0;
        return songRepository.deleteByContentHashIn(hashes);
    }

    @Transactional
    public boolean deleteByContentHash(String contentHash) {
        return songRepository.findByContentHash(contentHash)
                .map(song -> {
                    songRepository.delete(song);
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