package musarum.backend.favorite;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndSongId(Long userId, Long songId);

    void deleteByUserIdAndSongId(Long userId, Long songId);

    /** Borra todos los favoritos (de cualquier usuario) que apuntan a esa
     *  canción. Útil al eliminar la canción entera del servidor. */
    long deleteBySongId(Long songId);

    /** Snapshot plano para el BackupService: (user_id, song_id, created_at).
     *  Nativa para no cargar entidades Favorite completas en memoria. */
    @Query(value = "SELECT user_id, song_id, created_at FROM favorites ORDER BY user_id, song_id",
           nativeQuery = true)
    List<Object[]> dumpAll();

    /**
     * Favoritos recientes del catálogo: (user_id, song_id, created_at).
     * Lo consume el feed "Comunidad" del Home para sacar eventos tipo
     * "A marcó X como favorita hace Y". Nativa para evitar lazy-init de
     * las entidades — el service recompone con findAllById.
     */
    @Query(value = """
            SELECT user_id, song_id, created_at
            FROM favorites
            WHERE created_at > :since
            ORDER BY created_at DESC
            LIMIT :maxRows
            """, nativeQuery = true)
    List<Object[]> findRecentSince(java.time.Instant since, int maxRows);
}