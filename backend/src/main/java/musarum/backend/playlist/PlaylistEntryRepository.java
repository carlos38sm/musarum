package musarum.backend.playlist;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaylistEntryRepository extends JpaRepository<PlaylistEntry, Long> {
    Page<PlaylistEntry> findByPlaylistIdOrderByPositionAsc(Long playlistId, Pageable pageable);

    /** Entradas de cualquier playlist que apuntaban a esa canción. */
    long deleteBySongId(Long songId);
}