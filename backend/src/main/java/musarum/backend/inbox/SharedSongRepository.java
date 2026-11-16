package musarum.backend.inbox;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SharedSongRepository extends JpaRepository<SharedSong, Long> {
    java.util.List<SharedSong> findByToUserIdOrderByCreatedAtDesc(Long toUserId);
    long countByToUserIdAndReadAtIsNull(Long toUserId);
    long deleteBySongId(Long songId);
}
