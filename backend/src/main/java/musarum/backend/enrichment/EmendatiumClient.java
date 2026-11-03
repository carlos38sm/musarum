package musarum.backend.enrichment;

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import musarum.backend.enrichment.dto.EnrichRequest;
import musarum.backend.enrichment.dto.EnrichmentResponse;

/**
 * Cliente HTTP contra Emendatium (el servicio hermano de enriquecimiento).
 * Se usa fire-and-forget desde {@link EmendatiumAutoEnricher}. Si la URL no
 * está configurada, {@link #isEnabled()} devuelve false y las llamadas son no-op.
 */
@Component
public class EmendatiumClient {

    private static final Logger log = LoggerFactory.getLogger(EmendatiumClient.class);

    private final RestClient http;
    private final String baseUrl;

    public EmendatiumClient(@Value("${EMENDATIUM_BASE_URL:#{null}}") String baseUrl) {
        this.baseUrl = (baseUrl != null && !baseUrl.isBlank()) ? baseUrl.replaceAll("/$", "") : null;
        this.http = RestClient.builder().build();
        if (this.baseUrl != null) {
            log.info("EmendatiumClient habilitado hacia {}", this.baseUrl);
        }
    }

    public boolean isEnabled() {
        return baseUrl != null;
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
            log.debug("Emendatium no respondió para {}: {}", audioPath, e.getMessage());
            return Optional.empty();
        }
    }
}
