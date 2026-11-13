package musarum.backend.genre;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface GenreRepository extends JpaRepository<Genre, Long>{
    Optional<Genre> findByNameIgnoreCase(String name);

    /** Proyección para el ScannerCatalog: solo id + name. */
    @Query("SELECT g.id, g.name FROM Genre g")
    List<Object[]> findIdAndName();

    /** Géneros sin canciones asociadas. */
    @Query("SELECT g.id FROM Genre g "
         + "WHERE NOT EXISTS (SELECT 1 FROM Song s WHERE s.genre.id = g.id)")
    List<Long> findOrphanIds();
}