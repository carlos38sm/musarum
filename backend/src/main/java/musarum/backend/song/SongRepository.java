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
     * (ver {@link #findPageOrdered}). Letras primero (incluidas tildes y Ñ),
     * luego números/símbolos/no-latinos. Proyección mínima: no cargamos
     * entidades, solo strings.
     */
    @Query(value = """
        SELECT title FROM songs
        ORDER BY
          CASE WHEN title ~ '^[[:alpha:]]' THEN 0 ELSE 1 END,
          UPPER(title),
          id
        """, nativeQuery = true)
    List<String> findAllTitlesForIndex();

    /**
     * Página de canciones con el orden "humano":
     *   1) Primero las que empiezan por letra (A…Z, incluidas tildes y Ñ).
     *   2) Después números, símbolos y caracteres no latinos.
     * Dentro de cada grupo, por título en mayúsculas y id como desempate
     * estable. Nativo porque {@code [[:alpha:]]} es Postgres-specific y
     * cubre bien Unicode con la locale UTF-8 del servidor.
     */
    @Query(value = """
        SELECT * FROM songs
        ORDER BY
          CASE WHEN title ~ '^[[:alpha:]]' THEN 0 ELSE 1 END,
          UPPER(title),
          id
        LIMIT :size OFFSET :offset
        """, nativeQuery = true)
    List<Song> findPageOrdered(@org.springframework.data.repository.query.Param("size") int size,
                               @org.springframework.data.repository.query.Param("offset") int offset);

    Page<Song> findByTitleContainingIgnoreCase(String title, Pageable pageable);

    @Query(value = "SELECT * FROM songs ORDER BY RANDOM()", nativeQuery = true)
    List<Song> findAllShuffled();

    /**
     * Catálogo entero ordenado igual que la vista paginada (ver
     * {@link #findPageOrdered}). El móvil lo pide una sola vez al tocar una
     * canción desde Canciones para que la cola del player sea TODO el catálogo
     * y el botón shuffle de NowPlaying opere sobre el total, no solo sobre
     * las páginas ya descargadas.
     */
    @Query(value = """
        SELECT * FROM songs
        ORDER BY
          CASE WHEN title ~ '^[[:alpha:]]' THEN 0 ELSE 1 END,
          UPPER(title),
          id
        """, nativeQuery = true)
    List<Song> findAllOrdered();

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

    // ── Enrichment tracking (V6) ──────────────────────────────────────────

    /**
     * Candidatas a re-enriquecer: sin status (nunca intentada) o con
     * FAILED_TRANSIENT (fallo temporal). Las OK y FAILED_NO_MATCH se saltan
     * — repetirlas da el mismo resultado.
     */
    @Query("SELECT s FROM Song s "
         + "WHERE s.enrichStatus IS NULL OR s.enrichStatus = musarum.backend.song.EnrichStatus.FAILED_TRANSIENT")
    org.springframework.data.domain.Page<Song> findReenqueueable(
            org.springframework.data.domain.Pageable pageable);

    @Query("SELECT COUNT(s) FROM Song s WHERE s.enrichStatus IS NULL")
    long countEnrichPending();

    @Query("SELECT COUNT(s) FROM Song s WHERE s.enrichStatus = musarum.backend.song.EnrichStatus.OK")
    long countEnrichOk();

    @Query("SELECT COUNT(s) FROM Song s WHERE s.enrichStatus = musarum.backend.song.EnrichStatus.FAILED_NO_MATCH")
    long countEnrichNoMatch();

    @Query("SELECT COUNT(s) FROM Song s WHERE s.enrichStatus = musarum.backend.song.EnrichStatus.FAILED_TRANSIENT")
    long countEnrichTransient();

    // ── Home / Nuevos descubrimientos ─────────────────────────────────────

    /** Página de canciones creadas desde :since, orden desc por createdAt. */
    @Query("SELECT s FROM Song s WHERE s.createdAt >= :since ORDER BY s.createdAt DESC")
    Page<Song> findSinceOrderByCreatedDesc(java.time.Instant since, Pageable pageable);

    /** Cuántas canciones entraron al catálogo desde :since (para el badge). */
    @Query("SELECT COUNT(s) FROM Song s WHERE s.createdAt >= :since")
    long countSince(java.time.Instant since);

    /**
     * Posición 0-based de una canción en la vista paginada por defecto.
     * TIENE que usar EXACTAMENTE el mismo ORDER BY que {@link #findPageOrdered}
     * — si se desalinea, el FAB "ir a la canción que suena" scrollea al
     * sitio equivocado. Tripla ordenada ({@code CASE}, {@code UPPER(title)},
     * {@code id}) comparada lexicográficamente con ROW().
     */
    @Query(value = """
        SELECT COUNT(*) FROM songs
        WHERE
          (CASE WHEN title ~ '^[[:alpha:]]' THEN 0 ELSE 1 END, UPPER(title), id)
          <
          (CASE WHEN :t ~ '^[[:alpha:]]' THEN 0 ELSE 1 END, UPPER(CAST(:t AS text)), :i)
        """, nativeQuery = true)
    long countBefore(@org.springframework.data.repository.query.Param("t") String t,
                     @org.springframework.data.repository.query.Param("i") Long i);
}