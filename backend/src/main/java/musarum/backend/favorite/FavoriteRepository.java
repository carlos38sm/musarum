package musarum.backend.favorite;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface FavoriteRepository extends JpaRepository<Favorite, Long> {

    List<Favorite> findByUserIdOrderByCreatedAtDesc(Long userId);

    boolean existsByUserIdAndSongId(Long userId, Long songId);

    void deleteByUserIdAndSongId(Long userId, Long songId);

    /** Borra todos los favoritos (de cualquier usuario) que apuntan a esa
     *  canción. Útil al eliminar la canción entera del servidor. */
    long deleteBySongId(Long songId);
}