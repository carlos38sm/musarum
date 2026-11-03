package musarum.backend.version;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Expone la versión mínima/última del APK recomendado. El móvil lo consulta
 * al arrancar y, si {@code BuildConfig.VERSION_NAME < latestApk}, pide al
 * usuario que actualice (download abre el apkUrl en el navegador).
 *
 * Las versiones vienen de application.properties (editables sin recompilar
 * el backend). Si no están configuradas, devolvemos null → el móvil no
 * molesta al usuario.
 */
@RestController
class VersionController {

    private final String latestApk;
    private final String apkUrl;
    private final boolean mandatory;

    VersionController(
            @Value("${musarum.apk.latest:#{null}}") String latestApk,
            @Value("${musarum.apk.url:#{null}}") String apkUrl,
            @Value("${musarum.apk.mandatory:false}") boolean mandatory) {
        this.latestApk = latestApk;
        this.apkUrl = apkUrl;
        this.mandatory = mandatory;
    }

    @GetMapping("/version")
    VersionDto version() {
        return new VersionDto(latestApk, apkUrl, mandatory);
    }

    record VersionDto(String latestApk, String apkUrl, boolean mandatory) {}
}
