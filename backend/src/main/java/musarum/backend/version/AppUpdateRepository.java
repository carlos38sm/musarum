package musarum.backend.version;

import org.springframework.data.jpa.repository.JpaRepository;

interface AppUpdateRepository extends JpaRepository<AppUpdate, Long> {
}
