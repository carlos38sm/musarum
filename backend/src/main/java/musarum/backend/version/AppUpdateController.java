package musarum.backend.version;

import jakarta.validation.constraints.Size;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import musarum.backend.shared.security.CurrentUserResolver;

/**
 * Gestión del anuncio de versión del APK. OWNER-only.
 *
 * - GET  /admin/version → qué está publicado ahora (para pre-rellenar el form).
 * - POST /admin/version → actualiza el anuncio en caliente. Lo leen todos los
 *   móviles la próxima vez que hagan {@code GET /version} al arrancar.
 */
@RestController
@RequestMapping("/admin/version")
@PreAuthorize("hasRole('OWNER')")
class AppUpdateController {

    private final AppUpdateService service;
    private final CurrentUserResolver currentUser;

    AppUpdateController(AppUpdateService service, CurrentUserResolver currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @GetMapping
    ResponseEntity<AppUpdateDto> current() {
        return service.current()
                .map(AppUpdateDto::from)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.ok(new AppUpdateDto(null, null, false)));
    }

    @PostMapping
    AppUpdateDto publish(@RequestBody PublishRequest req) {
        Long userId = currentUser.currentUserId();
        AppUpdate updated = service.publish(req.latestApk(), req.apkUrl(), req.mandatory(), userId);
        return AppUpdateDto.from(updated);
    }

    public record PublishRequest(
            @Size(max = 32) String latestApk,
            @Size(max = 512) String apkUrl,
            boolean mandatory) {}

    public record AppUpdateDto(String latestApk, String apkUrl, boolean mandatory) {
        static AppUpdateDto from(AppUpdate row) {
            return new AppUpdateDto(row.getLatestApk(), row.getApkUrl(), row.isMandatory());
        }
    }
}
