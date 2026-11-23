package musarum.backend.admin;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import musarum.backend.version.AppUpdate;
import musarum.backend.version.AppUpdateService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Pipeline de "1 clic = todo actualizado" que dispara el OWNER desde Perfil:
 *
 * <ol>
 *   <li>Consulta GitHub Releases del repo. Busca el último tag
 *       {@code android-v*} y el último {@code backend-v*}.</li>
 *   <li>APK: si el tag detectado difiere del anuncio actual en la DB
 *       ({@link AppUpdateService}), publica el anuncio (lo que hoy se hace
 *       a mano en la pantalla "Publicar actualización"). Los móviles ven
 *       el diálogo "Actualizar" al reabrir.</li>
 *   <li>Backend: dispara la HTTP API de Watchtower
 *       ({@code POST /v1/update} con {@code Authorization: Bearer <token>}).
 *       Watchtower pulla la imagen nueva y recrea el contenedor al instante.
 *       NO comparamos tags: Watchtower ya sabe si hay cambio por digest, y
 *       pedirle un update a mano cuando no hay nada nuevo es un no-op.</li>
 * </ol>
 *
 * Devuelve siempre un reporte estructurado para que el móvil pinte qué
 * se hizo y qué falló (no revienta: los errores se acumulan en la lista).
 */
@Service
class UpdateCheckService {

    private static final Logger log = LoggerFactory.getLogger(UpdateCheckService.class);

    private final AppUpdateService appUpdateService;
    private final ObjectMapper mapper = new ObjectMapper();
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    private final String githubRepo;
    private final String apkUrlPattern;
    private final String watchtowerUrl;
    private final String watchtowerToken;

    UpdateCheckService(AppUpdateService appUpdateService,
                       @Value("${musarum.github.repo:}") String githubRepo,
                       @Value("${musarum.apk.url-pattern:}") String apkUrlPattern,
                       @Value("${musarum.watchtower.url:}") String watchtowerUrl,
                       @Value("${musarum.watchtower.token:}") String watchtowerToken) {
        this.appUpdateService = appUpdateService;
        this.githubRepo = normalize(githubRepo);
        this.apkUrlPattern = normalize(apkUrlPattern);
        this.watchtowerUrl = normalize(watchtowerUrl);
        this.watchtowerToken = normalize(watchtowerToken);
    }

    UpdateReport runAll(Long requesterId) {
        List<String> errors = new ArrayList<>();
        ApkCheck apk = checkApk(requesterId, errors);
        BackendCheck backend = checkBackend(errors);
        return new UpdateReport(apk, backend, errors);
    }

    private ApkCheck checkApk(Long requesterId, List<String> errors) {
        if (githubRepo == null) {
            errors.add("GitHub repo no configurado (MUSARUM_GITHUB_REPO).");
            return new ApkCheck(null, null, false);
        }
        String tag = fetchLatestTag("android-v", errors);
        if (tag == null) return new ApkCheck(null, null, false);
        String version = tag.substring("android-v".length());

        Optional<AppUpdate> cur = appUpdateService.current();
        String announced = cur.map(AppUpdate::getLatestApk).orElse(null);
        if (version.equals(announced)) {
            // Ya estaba anunciada.
            return new ApkCheck(version, announced, false);
        }

        String pattern = apkUrlPattern != null
                ? apkUrlPattern
                : "https://github.com/" + githubRepo
                        + "/releases/download/android-v{v}/musarum-{v}.apk";
        String url = pattern.replace("{v}", version);

        try {
            appUpdateService.publish(version, url, false, requesterId);
            return new ApkCheck(version, announced, true);
        } catch (Exception e) {
            errors.add("Publicando APK: " + e.getMessage());
            return new ApkCheck(version, announced, false);
        }
    }

    private BackendCheck checkBackend(List<String> errors) {
        if (githubRepo == null) {
            // Solo informativo — Watchtower podría estar configurado igual.
            errors.add("Repo GitHub no configurado; no se detecta el último tag backend.");
        }
        String tag = githubRepo != null ? fetchLatestTag("backend-v", errors) : null;

        if (watchtowerUrl == null || watchtowerToken == null) {
            errors.add("Watchtower no configurado (URL o TOKEN vacíos).");
            return new BackendCheck(tag, false);
        }
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(watchtowerUrl + "/v1/update"))
                    .header("Authorization", "Bearer " + watchtowerToken)
                    .timeout(Duration.ofSeconds(60))
                    .POST(HttpRequest.BodyPublishers.noBody())
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() / 100 != 2) {
                errors.add("Watchtower respondió HTTP " + resp.statusCode());
                return new BackendCheck(tag, false);
            }
            // Watchtower responde con texto plano ("Updates triggered"); lo loggeamos.
            log.info("Watchtower update disparado: {}", resp.body().trim());
            return new BackendCheck(tag, true);
        } catch (Exception e) {
            errors.add("Watchtower: " + e.getMessage());
            return new BackendCheck(tag, false);
        }
    }

    /**
     * Devuelve el tag más reciente (según orden de GitHub Releases) que
     * empieza por {@code prefix}. El endpoint devuelve máx 30 por página,
     * basta para nuestros 3 canales. Si no responde 2xx o no hay match,
     * devuelve null y añade a {@code errors}.
     */
    private String fetchLatestTag(String prefix, List<String> errors) {
        if (githubRepo == null) return null;
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create("https://api.github.com/repos/" + githubRepo
                            + "/releases?per_page=30"))
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "musarum-update-check")
                    .timeout(Duration.ofSeconds(10))
                    .GET()
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                errors.add("GitHub Releases (" + prefix + "): HTTP " + resp.statusCode());
                return null;
            }
            JsonNode arr = mapper.readTree(resp.body());
            for (JsonNode node : arr) {
                JsonNode nameNode = node.get("tag_name");
                if (nameNode == null || nameNode.isNull()) continue;
                String tag = nameNode.asText();
                if (tag.startsWith(prefix)) return tag;
            }
            errors.add("No encontré ningún release con prefijo " + prefix);
            return null;
        } catch (Exception e) {
            errors.add("GitHub (" + prefix + "): " + e.getMessage());
            return null;
        }
    }

    private static String normalize(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    // ── DTOs públicos ────────────────────────────────────────────────────

    public record ApkCheck(String latestTag, String previouslyAnnounced, boolean published) {}
    public record BackendCheck(String latestTag, boolean watchtowerTriggered) {}
    public record UpdateReport(ApkCheck apk, BackendCheck backend, List<String> errors) {}
}
