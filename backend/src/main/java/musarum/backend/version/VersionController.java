package musarum.backend.version;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone la versión mínima/última del APK recomendado. El móvil lo consulta
 * al arrancar y, si {@code BuildConfig.VERSION_NAME < latestApk}, pide al
 * usuario que actualice (download abre el apkUrl en el navegador).
 *
 * Fuente preferida: tabla {@code app_updates} (editable en caliente desde
 * Perfil → Publicar actualización en el móvil, OWNER-only). Fallback:
 * properties {@code musarum.apk.*} del {@code .env}, por compatibilidad
 * con instalaciones previas. Si ninguna fuente tiene datos, devolvemos
 * campos vacíos → el móvil detecta la ausencia y no molesta al usuario.
 */
@RestController
class VersionController {

    private final AppUpdateService appUpdateService;
    private final String propLatestApk;
    private final String propApkUrl;
    private final boolean propMandatory;

    VersionController(
            AppUpdateService appUpdateService,
            @Value("${musarum.apk.latest:#{null}}") String propLatestApk,
            @Value("${musarum.apk.url:#{null}}") String propApkUrl,
            @Value("${musarum.apk.mandatory:false}") boolean propMandatory) {
        this.appUpdateService = appUpdateService;
        this.propLatestApk = propLatestApk;
        this.propApkUrl = propApkUrl;
        this.propMandatory = propMandatory;
    }

    @GetMapping("/version")
    VersionDto version() {
        // Si la fila singleton tiene algo, gana. "Algo" = latestApk no nulo
        // ni vacío (una fila recién migrada con todo NULL cae al fallback).
        return appUpdateService.current()
                .filter(row -> row.getLatestApk() != null && !row.getLatestApk().isBlank())
                .map(row -> new VersionDto(row.getLatestApk(), row.getApkUrl(), row.isMandatory()))
                .orElseGet(() -> new VersionDto(
                        emptyToNull(propLatestApk),
                        emptyToNull(propApkUrl),
                        propMandatory));
    }

    private static String emptyToNull(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }

    record VersionDto(String latestApk, String apkUrl, boolean mandatory) {}
}
