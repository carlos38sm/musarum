package musarum.backend.version;

import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class AppUpdateService {

    private final AppUpdateRepository repo;

    AppUpdateService(AppUpdateRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    Optional<AppUpdate> current() {
        return repo.findById(AppUpdate.SINGLETON_ID);
    }

    @Transactional
    AppUpdate publish(String latestApk, String apkUrl, boolean mandatory, Long updatedBy) {
        AppUpdate row = repo.findById(AppUpdate.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "La fila singleton de app_updates no existe. ¿Falta la migración V5?"));
        row.update(normalize(latestApk), normalize(apkUrl), mandatory, updatedBy);
        return repo.save(row);
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String trimmed = s.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
