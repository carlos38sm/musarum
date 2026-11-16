package musarum.backend.playlist;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PlaylistEntryRepository extends JpaRepository<PlaylistEntry, Long> {
    Page<PlaylistEntry> findByPlaylistIdOrderByPositionAsc(Long playlistId, Pageable pageable);

    /** Todas las entradas de una playlist, sin paginar — para backups / export. */
    java.util.List<PlaylistEntry> findByPlaylistIdOrderByPositionAsc(Long playlistId);

    /** Entradas de cualquier playlist que apuntaban a esa canción. */
    long deleteBySongId(Long songId);

    /** Snapshot plano para BackupService: (playlist_id, song_id, position) ordenado. */
    @org.springframework.data.jpa.repository.Query(
            value = "SELECT playlist_id, song_id, \"position\" FROM playlist_entries " +
                    "ORDER BY playlist_id, \"position\"",
            nativeQuery = true)
    java.util.List<Object[]> dumpAll();
}