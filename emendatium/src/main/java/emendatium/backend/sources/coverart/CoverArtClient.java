package emendatium.backend.sources.coverart;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Comprobador de portadas en CoverArtArchive. Cacheamos la existencia por
 * mbReleaseId para no bombardear el servicio con HEADs repetidos entre
 * canciones del mismo álbum.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CoverArtClient {

    static final String BASE_URL = "https://coverartarchive.org/release/";

    private final RestClient enricherRestClient;

    @Cacheable(value = "coverArt")
    public boolean coverExists(String mbReleaseId) {
        if (mbReleaseId == null || mbReleaseId.isBlank()) return false;
        try {
            enricherRestClient.get()
                    .uri(frontUrl(mbReleaseId))
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (Exception e) {
            log.info("CoverArtArchive sin portada para release {}: {}", mbReleaseId, e.getMessage());
            return false;
        }
    }

    public String frontUrl(String mbReleaseId) {
        return BASE_URL + mbReleaseId + "/front-500";
    }
}
