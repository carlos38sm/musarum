package musarum.backend.reproduction;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ReproductionRepository extends JpaRepository<Reproduction, Long> {

    /**
     * IDs de canción ordenados por nº de reproducciones (descendente) dentro
     * del rango temporal. Pageable controla el límite.
     */
    @Query("""
            SELECT r.songId FROM Reproduction r
            WHERE r.userId = :userId AND r.createdAt >= :since
            GROUP BY r.songId
            ORDER BY COUNT(r) DESC
            """)
    List<Long> findTopSongIds(Long userId, Instant since, Pageable pageable);

    /**
     * IDs de canción distintas ordenadas por última reproducción (más
     * reciente primero). Útil para "Vuelve a escuchar".
     */
    @Query("""
            SELECT r.songId FROM Reproduction r
            WHERE r.userId = :userId
            GROUP BY r.songId
            ORDER BY MAX(r.createdAt) DESC
            """)
    List<Long> findRecentSongIds(Long userId, Pageable pageable);

    /** Última reproducción del usuario (para el "Continúa escuchando"). */
    @Query("""
            SELECT r.songId FROM Reproduction r
            WHERE r.userId = :userId
            ORDER BY r.createdAt DESC
            """)
    List<Long> findLastPlayedSongIds(Long userId, Pageable pageable);

    long countByUserId(Long userId);

    @Query("SELECT COUNT(DISTINCT r.songId) FROM Reproduction r WHERE r.userId = :userId")
    long countDistinctSongsByUserId(Long userId);

    /** Borra el histórico (de cualquier usuario) que apunta a esa canción. */
    long deleteBySongId(Long songId);

    /**
     * Borra todas las reproducciones del usuario. Lo usa el OWNER al eliminar
     * un user del sistema. Antes se tragaba findAll() entero y filtraba en
     * memoria — con historial grande (decenas de miles) era OOM seguro.
     */
    long deleteByUserId(Long userId);

    /**
     * IDs de canción por frecuencia descendente (sin ventana temporal). Lo
     * usa el servicio de stats para calcular el "más escuchado" global.
     */
    @Query("""
            SELECT r.songId FROM Reproduction r
            WHERE r.userId = :userId
            GROUP BY r.songId
            ORDER BY COUNT(r) DESC
            """)
    List<Long> findSongIdsOrderedByPlaysDesc(Long userId, Pageable pageable);

    // ── Métricas globales para el panel de servidor (OWNER) ──

    @Query("SELECT COUNT(r) FROM Reproduction r WHERE r.createdAt >= :since")
    long countSince(Instant since);

    @Query("SELECT COUNT(DISTINCT r.userId) FROM Reproduction r WHERE r.createdAt >= :since")
    long countDistinctUsersSince(Instant since);

    @Query("SELECT MAX(r.createdAt) FROM Reproduction r")
    Instant findLastReproductionAt();

    // ── Home / convivencia ────────────────────────────────────────────────

    /**
     * Última reproducción POR USUARIO desde un instante dado. Devuelve una
     * fila por usuario activo (la más reciente de cada uno). Se usa para
     * pintar "En Musarum ahora" sin tirarnos a scan de toda la tabla.
     */
    @Query("""
            SELECT r FROM Reproduction r
            WHERE r.createdAt >= :since
              AND r.createdAt = (
                SELECT MAX(r2.createdAt) FROM Reproduction r2
                WHERE r2.userId = r.userId AND r2.createdAt >= :since
              )
            ORDER BY r.createdAt DESC
            """)
    List<Reproduction> findLastPerUserSince(Instant since);

    /** Nº de reproducciones del usuario desde un instante. */
    @Query("SELECT COUNT(r) FROM Reproduction r WHERE r.userId = :userId AND r.createdAt >= :since")
    long countByUserSince(Long userId, Instant since);

    /**
     * (songId, plays) de un usuario desde :since ordenado por plays desc.
     * Lo usa el mini-perfil para sacar su "canción más escuchada" en un paso.
     */
    @Query("""
            SELECT r.songId, COUNT(r) FROM Reproduction r
            WHERE r.userId = :userId AND r.createdAt >= :since
            GROUP BY r.songId
            ORDER BY COUNT(r) DESC
            """)
    List<Object[]> findTopSongsWithCount(Long userId, Instant since, Pageable pageable);

    /**
     * Top artista del usuario en una ventana temporal. Agrega plays a través
     * de song_artists para que una canción con 3 artistas los puntúe a todos.
     * Devuelve (artistId, plays) ordenado por plays desc, LIMIT impuesto por
     * el SQL (1).
     */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT sa.artist_id, SUM(c.cnt) AS plays
            FROM (
              SELECT song_id, COUNT(*) AS cnt FROM reproductions
              WHERE user_id = :userId AND created_at >= :since
              GROUP BY song_id
            ) AS c
            JOIN song_artists sa ON sa.song_id = c.song_id
            GROUP BY sa.artist_id
            ORDER BY plays DESC
            LIMIT 1
            """, nativeQuery = true)
    List<Object[]> findTopArtistForUserSince(Long userId, Instant since);

    /** Top género del usuario en una ventana temporal. (genreId, plays). */
    @org.springframework.data.jpa.repository.Query(value = """
            SELECT s.genre_id, COUNT(r.id) AS plays
            FROM reproductions r
            JOIN songs s ON s.id = r.song_id
            WHERE r.user_id = :userId AND r.created_at >= :since AND s.genre_id IS NOT NULL
            GROUP BY s.genre_id
            ORDER BY plays DESC
            LIMIT 1
            """, nativeQuery = true)
    List<Object[]> findTopGenreForUserSince(Long userId, Instant since);
}
