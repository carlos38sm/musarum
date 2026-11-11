package musarum.backend.artist;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface ArtistRepository extends JpaRepository<Artist, Long> {
    Optional<Artist> findByNameIgnoreCase(String name);
    Optional<Artist> findByNameKey(String nameKey);
    Optional<Artist> findByMbId(String mbId);
    boolean existsByName(String name);
    List<Artist> findByNameContainingIgnoreCase(String name);
    Page<Artist> findByNameContainingIgnoreCase(String name, Pageable pageable);
    Page<Artist> findAll(Pageable pageable);
    List<Artist> findAllByNameKeyIsNull();

    @Modifying
    @Query(value = """
            UPDATE song_artists SET artist_id = :canonicalId
            WHERE artist_id = :duplicateId
              AND NOT EXISTS (
                  SELECT 1 FROM song_artists sa
                  WHERE sa.song_id = song_artists.song_id
                    AND sa.artist_id = :canonicalId
              )
            """, nativeQuery = true)
    int reassignSongArtists(Long canonicalId, Long duplicateId);

    @Modifying
    @Query(value = "DELETE FROM song_artists WHERE artist_id = :duplicateId", nativeQuery = true)
    int deleteSongArtistsForArtist(Long duplicateId);

    @Modifying
    @Query(value = "UPDATE albums SET artist_id = :canonicalId WHERE artist_id = :duplicateId",
            nativeQuery = true)
    int reassignAlbumsArtist(Long canonicalId, Long duplicateId);

    /**
     * Proyección para el ScannerCatalog: solo id + nameKey, sin cargar la
     * entidad completa. Con 1-2k artistas evita leer biografías gigantes.
     */
    @Query("SELECT a.id, a.nameKey FROM Artist a WHERE a.nameKey IS NOT NULL")
    List<Object[]> findIdAndNameKey();
}
