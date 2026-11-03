package musarum.backend.playlist;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaylistRepository extends JpaRepository<Playlist, Long> {

    List<Playlist> findByOwnerId(Long ownerId);

    List<Playlist> findByIsPublicTrue();

    @Query("SELECT COALESCE(MAX(e.position), 0) FROM PlaylistEntry e WHERE e.playlist.id = :playlistId")
    Integer findMaxPositionInPlaylist(Long playlistId);

    Optional<Playlist> findByIdAndOwnerId(Long id, Long ownerId);

    @Query("SELECT p FROM Playlist p JOIN p.collaborators c WHERE c.id = :userId")
    List<Playlist> findByCollaboratorId(Long userId);
}