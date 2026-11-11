package musarum.backend.enrichment;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import musarum.backend.enrichment.dto.EnrichRequest;
import musarum.backend.enrichment.dto.EnrichmentResponse;

/**
 * Cliente HTTP contra Emendatium (el servicio hermano de enriquecimiento).
 * Se usa fire-and-forget desde {@link EmendatiumAutoEnricher}. Si la URL no
 * está configurada, {@link #isEnabled()} devuelve false y las llamadas son no-op.
 *
 * <p>Cachea el resultado del último ping a {@code /actuator/health} para que
 * el panel "Análisis del servidor" pueda mostrar en caliente si Emendatium
 * está alcanzable sin pagar un round trip en cada stats.
 */
@Component
public class EmendatiumClient {

    private static final Logger log = LoggerFactory.getLogger(EmendatiumClient.class);

    // Cuánto puede tardar la lectura del enrich: el cálculo FFT + ReplayGain
    // + fingerprint de un fichero es I/O + CPU, 60 s cubre el peor caso en
    // portátil viejo. Más bajo y recortamos canciones largas.
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    // El ping cachea 30 s. Evita martillear /actuator/health si el OWNER
    // refresca el panel rápido pero sigue siendo fresco.
    private static final Duration PING_TTL = Duration.ofSeconds(30);

    private final RestClient http;
    private final String baseUrl;

    private volatile HealthSnapshot cachedHealth = null;

    public EmendatiumClient(@Value("${EMENDATIUM_BASE_URL:#{null}}") String baseUrl) {
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/$", "") : null;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT);
        factory.setReadTimeout(READ_TIMEOUT);
        this.http = RestClient.builder().requestFactory(factory).build();

        if (this.baseUrl != null) {
            log.info("EmendatiumClient habilitado hacia {} (connect={}, read={})",
                    this.baseUrl, CONNECT_TIMEOUT, READ_TIMEOUT);
        } else {
            log.info("EmendatiumClient deshabilitado (EMENDATIUM_BASE_URL no definida)");
        }
    }

    public boolean isEnabled() {
        return baseUrl != null;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public Optional<EnrichmentResponse> enrich(String audioPath) {
        if (!isEnabled()) return Optional.empty();
        try {
            EnrichmentResponse resp = http.post()
                    .uri(baseUrl + "/api/enrich")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EnrichRequest(audioPath))
                    .retrieve()
                    .body(EnrichmentResponse.class);
            return Optional.ofNullable(resp);
        } catch (Exception e) {
            // Antes era log.debug (invisible con nivel INFO por defecto). Si
            // está habilitado y falla, subimos a WARN para que el OWNER pueda
            // ver en docker logs qué pasa sin tocar niveles.
            log.warn("Emendatium no respondió para {}: {}", audioPath, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Snapshot del estado de Emendatium para el panel de servidor. Hace ping
     * a {@code /actuator/health} con cache de {@link #PING_TTL} para no pagar
     * un round trip en cada refresh. Siempre devuelve algo — si no alcanzable,
     * {@code up=false} con el mensaje de la excepción.
     */
    public HealthSnapshot health() {
        if (!isEnabled()) {
            return new HealthSnapshot(false, false, "EMENDATIUM_BASE_URL no configurada", null, Instant.now());
        }
        HealthSnapshot cached = cachedHealth;
        if (cached != null && Duration.between(cached.checkedAt(), Instant.now()).compareTo(PING_TTL) < 0) {
            return cached;
        }
        try {
            // Timeout corto para el ping: no queremos bloquear el panel si
            // Emendatium está caído.
            SimpleClientHttpRequestFactory fastFactory = new SimpleClientHttpRequestFactory();
            fastFactory.setConnectTimeout(Duration.ofSeconds(2));
            fastFactory.setReadTimeout(Duration.ofSeconds(3));
            RestClient fast = RestClient.builder().requestFactory(fastFactory).build();

            String body = fast.get()
                    .uri(baseUrl + "/actuator/health")
                    .retrieve()
                    .body(String.class);
            // Spring Actuator devuelve {"status":"UP"} o similar. Basta con
            // que contenga "UP" para considerarlo vivo.
            boolean up = body != null && body.contains("\"UP\"");
            HealthSnapshot snap = new HealthSnapshot(true, up,
                    up ? null : ("Respuesta inesperada: " + body),
                    body, Instant.now());
            cachedHealth = snap;
            return snap;
        } catch (Exception e) {
            HealthSnapshot snap = new HealthSnapshot(true, false, e.getMessage(), null, Instant.now());
            cachedHealth = snap;
            return snap;
        }
    }

    /**
     * @param enabled   true si EMENDATIUM_BASE_URL está configurada.
     * @param reachable true si {@code /actuator/health} respondió "UP".
     * @param message   motivo de fallo o null si OK.
     * @param rawBody   respuesta cruda del actuator (útil para diagnosticar
     *                  cuándo responde pero con componentes DOWN).
     * @param checkedAt cuándo se hizo el ping.
     */
    public record HealthSnapshot(
            boolean enabled,
            boolean reachable,
            String message,
            String rawBody,
            Instant checkedAt
    ) {}
}
