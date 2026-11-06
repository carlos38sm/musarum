package musarum.backend.song;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface SongRepository extends JpaRepository<Song, Long> {
    Page<Song> findByAlbumIdOrderByTrackNumberAsc(Long albumId, Pageable pageable);
    Optional<Song> findByAudioPath(String audioPath);
    Optional<Song> findByContentHash(String contentHash);

    @Query("SELECT s FROM Song s JOIN s.artists a WHERE a.id = :artistId")
    Page<Song> findByArtistId(Long artistId, Pageable pageable);

    Page<Song> findByGenreId(Long genreId, Pageable pageable);
    long countByGenreId(Long genreId);

    @Query("SELECT COUNT(DISTINCT s) FROM Song s JOIN s.artists a WHERE a.id = :artistId")
    long countByArtistId(Long artistId);

    long countByAlbumId(Long albumId);

    @Query("SELECT COALESCE(SUM(s.durationMs), 0) FROM Song s WHERE s.album.id = :albumId")
    long sumDurationMsByAlbumId(Long albumId);

    @Query("SELECT COALESCE(SUM(DISTINCT s.durationMs), 0) FROM Song s JOIN s.artists a WHERE a.id = :artistId")
    long sumDurationMsByArtistId(Long artistId);

    /**
     * Agrega por lotes para no caer en N+1 cuando pintamos la discografía
     * de un artista. Devuelve filas [albumId, songCount, totalDurationMs].
     */
    @Query("SELECT s.album.id, COUNT(s), COALESCE(SUM(s.durationMs), 0) " +
           "FROM Song s WHERE s.album.id IN :albumIds GROUP BY s.album.id")
    List<Object[]> aggregateByAlbumIds(java.util.Collection<Long> albumIds);

    @Query("SELECT s.audioPath FROM Song s")
    Set<String> findAllAudioPaths();

    /**
     * Cuántas canciones viven bajo una carpeta dada. "LIKE prefix/%": las
     * entradas se guardan con "/" como separador (ver LibraryScanner.normalize),
     * así que esto funciona igual en Windows que en Linux.
     */
    @Query("SELECT COUNT(s) FROM Song s WHERE s.audioPath LIKE CONCAT(:prefix, '/%') "
         + "OR s.audioPath = :prefix")
    long countByAudioPathPrefix(String prefix);

    @Query("SELECT s.contentHash FROM Song s")
    Set<String> findAllContentHashes();

    int deleteByAudioPathIn(Set<String> audioPaths);
    int deleteByContentHashIn(Set<String> contentHashes);
    Page<Song> findAll(Pageable pageable);

    /**
     * Solo títulos, ordenados igual que la vista paginada por defecto
     * ({@code sort=title,asc}). Proyección mínima: no cargamos entidades,
     * solo strings. En Postgres con el collation estándar en_US.UTF-8
     * mayúsculas y minúsculas quedan intercaladas, que es lo que espera
     * el móvil al calcular los offsets del AlphabetJumpBar.
     */
    @Query("SELECT s.title FROM Song s ORDER BY s.title ASC")
    List<String> findAllTitlesForIndex();

    Page<Song> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    @Query(value = "SELECT * FROM songs ORDER BY RANDOM()", nativeQuery = true)
    List<Song> findAllShuffled();

    // ── Agregados para el panel de servidor (OWNER) ──

    @Query("SELECT COUNT(s) FROM Song s WHERE s.lyricsPath IS NOT NULL AND s.lyricsPath <> ''")
    long countWithLyrics();

    @Query("SELECT COALESCE(SUM(s.fileSize), 0) FROM Song s")
    long sumFileSize();

    @Query("SELECT COUNT(s) FROM Song s WHERE s.bpm IS NULL")
    long countMissingBpm();

    @Query("SELECT COUNT(s) FROM Song s WHERE s.musicalKey IS NULL OR s.musicalKey = ''")
    long countMissingKey();

    @Query("SELECT COUNT(s) FROM Song s WHERE s.replayGain IS NULL")
    long countMissingReplayGain();

    @Query("SELECT COUNT(s) FROM Song s WHERE s.mbId IS NULL OR s.mbId = ''")
    long countMissingMbid();

    /** Canciones cuyo álbum NO tiene cover_url. */
    @Query("SELECT COUNT(s) FROM Song s WHERE s.album IS NULL OR s.album.coverUrl IS NULL OR s.album.coverUrl = ''")
    long countMissingCover();

    @Query("SELECT s.id FROM Song s WHERE s.lyricsPath IS NOT NULL AND s.lyricsPath <> ''")
    List<Long> findIdsWithLyrics();

    /** Solo los IDs — se usa en las estadísticas para no cargar entidades enteras. */
    @Query("SELECT s.id FROM Song s")
    List<Long> findAllIds();

    /**
     * Posición 0-based de una canción en la vista paginada por defecto
     * (ORDER BY title ASC, id ASC como desempate estable). Cuenta cuántas
     * canciones van ANTES de la dada. Se usa para "ir a la canción que
     * suena" desde la lista — el móvil hace page = index / size y scroll.
     */
    @Query("SELECT COUNT(s) FROM Song s WHERE s.title < :t "
         + "OR (s.title = :t AND s.id < :i)")
    long countBefore(@org.springframework.data.repository.query.Param("t") String t,
                     @org.springframework.data.repository.query.Param("i") Long i);
}