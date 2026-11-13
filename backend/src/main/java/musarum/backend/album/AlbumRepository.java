package musarum.backend.album;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface AlbumRepository extends JpaRepository<Album, Long> {
    Page<Album> findByArtistId(Long artistId, Pageable pageable);
    long countByArtistId(Long artistId);
    Optional<Album> findByTitleIgnoreCaseAndArtistId(String title, Long artistId);
    Optional<Album> findByTitleKeyAndArtistId(String titleKey, Long artistId);
    Optional<Album> findByMbId(String mbId);
    Page<Album> findByTitleContainingIgnoreCase(String title, Pageable pageable);
    List<Album> findAllByTitleKeyIsNull();

    @Modifying
    @Query(value = "UPDATE songs SET album_id = :canonicalId WHERE album_id = :duplicateId",
            nativeQuery = true)
    int reassignSongsAlbum(Long canonicalId, Long duplicateId);

    /**
     * Proyección para el ScannerCatalog: id, titleKey, artistId. Rompe el
     * N+1 que haría una iteración de findAll() tocando album.getArtist().
     */
    @Query("SELECT a.id, a.titleKey, a.artist.id FROM Album a "
         + "WHERE a.titleKey IS NOT NULL AND a.artist IS NOT NULL")
    List<Object[]> findIdTitleKeyAndArtistId();

    /** Álbumes sin canciones asociadas. */
    @Query("SELECT a.id FROM Album a "
         + "WHERE NOT EXISTS (SELECT 1 FROM Song s WHERE s.album.id = a.id)")
    List<Long> findOrphanIds();
}
