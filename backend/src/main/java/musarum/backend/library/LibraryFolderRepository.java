package musarum.backend.library;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface LibraryFolderRepository extends JpaRepository<LibraryFolder, Long> {

    List<LibraryFolder> findAllByOrderByPathAsc();

    List<LibraryFolder> findAllByActiveTrueOrderByPathAsc();

    Optional<LibraryFolder> findByPath(String path);

    boolean existsByPath(String path);
}
