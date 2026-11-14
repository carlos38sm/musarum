package musarum.backend.user;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface UserRepository extends JpaRepository<User, Long>{
	Optional<User> findByNick(String nick);
	boolean existsByNick(String nick);
	List<User> findByNickContainingIgnoreCaseOrderByNickAsc(String query, Pageable pageable);
	long countByBlocked(boolean blocked);
	long countByUserRole(UserRole userRole);
}