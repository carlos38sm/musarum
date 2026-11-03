package musarum.backend.enrichment;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import musarum.backend.enrichment.dto.BatchJobStatus;
import musarum.backend.enrichment.dto.BatchStartRequest;
import musarum.backend.enrichment.dto.EnrichRequest;
import musarum.backend.enrichment.dto.EnrichmentResponse;
import musarum.backend.enrichment.exception.EnrichmentFailedException;

@Component
public class EnrichmentClient {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentClient.class);

    private final RestClient restClient;

    EnrichmentClient(@Value("${EMENDATIUM_BASE_URL}") String baseUrl) {
        this.restClient = RestClient.builder()
                .baseUrl(baseUrl)
                .build();
    }

    public EnrichmentResponse enrich(String audioPath) {
        try {
            return restClient.post()
                    .uri("/api/enrich")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EnrichRequest(audioPath))
                    .retrieve()
                    .body(EnrichmentResponse.class);
        } catch (Exception e) {
            log.error("Fallo llamando a Emendatium con path '{}': {}", audioPath, e.getMessage(), e);
            throw new EnrichmentFailedException(audioPath, e);
        }
    }

    public BatchJobStatus startBatch(String folderPath) {
        try {
            return restClient.post()
                    .uri("/api/enrich/batch")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new BatchStartRequest(folderPath))
                    .retrieve()
                    .body(BatchJobStatus.class);
        } catch (Exception e) {
            log.error("Fallo arrancando batch en '{}': {}", folderPath, e.getMessage(), e);
            throw new EnrichmentFailedException(folderPath, e);
        }
    }

    public BatchJobStatus getBatchStatus(String jobId) {
        try {
            return restClient.get()
                    .uri("/api/enrich/batch/{jobId}", jobId)
                    .retrieve()
                    .body(BatchJobStatus.class);
        } catch (Exception e) {
            log.error("Fallo consultando estado del batch '{}': {}", jobId, e.getMessage(), e);
            throw new EnrichmentFailedException(jobId, e);
        }
    }
}