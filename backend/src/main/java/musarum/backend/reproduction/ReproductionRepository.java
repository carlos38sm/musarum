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
}
