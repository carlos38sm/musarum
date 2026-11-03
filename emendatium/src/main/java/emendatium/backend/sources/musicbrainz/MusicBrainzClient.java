package emendatium.backend.sources.musicbrainz;

import emendatium.backend.config.EnricherProperties;
import emendatium.backend.shared.LogSafe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Cliente de MusicBrainz con reintentos y rate limit configurable. Por defecto
 * 1 req/s y 1 concurrente (API pública); subibles cuando hay servidor propio o
 * token. Si hay token, se envía como Bearer en la cabecera Authorization. Los
 * resultados válidos se cachean; los fallos NO, para poder reintentarlos luego.
 */
@Slf4j
@Component
public class MusicBrainzClient {

    private static final String RECORDING_URL = "https://musicbrainz.org/ws/2/recording/";
    private static final String RELEASE_URL   = "https://musicbrainz.org/ws/2/release/";

    private static final String RECORDING_INC =
            "artist-credits+releases+release-groups+media+isrcs+genres+tags";
    private static final String RELEASE_INC = "labels";

    private static final int INTENTOS = 2;

    private final RestClient http;
    private final Semaphore concurrencyGate;
    private final long minIntervalMs;
    private final String apiToken;

    public MusicBrainzClient(RestClient enricherRestClient, EnricherProperties props) {
        this.http = enricherRestClient;
        EnricherProperties.Sources.Musicbrainz mb = props.sources().musicbrainz();
        this.concurrencyGate = new Semaphore(mb.maxConcurrent(), true);
        this.minIntervalMs = Math.max(1L, (long) Math.ceil(1000.0 / mb.requestsPerSecond()));
        this.apiToken = (mb.apiToken() != null && !mb.apiToken().isBlank()) ? mb.apiToken() : null;
        log.info("MusicBrainz client: {} req/s, {} concurrente(s), token={}",
                mb.requestsPerSecond(), mb.maxConcurrent(), apiToken != null ? "sí" : "no");
    }

    @Cacheable(value = "mbRecording", unless = "#result == null")
    public Optional<MusicBrainzResponse> lookupRecording(String mbid) {
        return conReintentos(() -> http.get()
                .uri(RECORDING_URL + mbid + "?inc=" + RECORDING_INC + "&fmt=json")
                .headers(this::applyAuth)
                .retrieve().body(MusicBrainzResponse.class), "recording " + mbid);
    }

    @Cacheable(value = "mbRelease", unless = "#result == null")
    public Optional<MusicBrainzResponse.Release> lookupReleaseLabels(String releaseId) {
        return conReintentos(() -> http.get()
                .uri(RELEASE_URL + releaseId + "?inc=" + RELEASE_INC + "&fmt=json")
                .headers(this::applyAuth)
                .retrieve().body(MusicBrainzResponse.Release.class), "release " + releaseId);
    }

    private void applyAuth(HttpHeaders headers) {
        if (apiToken != null) headers.setBearerAuth(apiToken);
    }

    private <T> Optional<T> conReintentos(Supplier<T> llamada, String desc) {
        for (int i = 1; i <= INTENTOS; i++) {
            try {
                concurrencyGate.acquire();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
            try {
                T result = llamada.get();
                dormir(minIntervalMs);
                return Optional.ofNullable(result);
            } catch (Exception e) {
                dormir(minIntervalMs);
                if (i < INTENTOS) {
                    log.info("MusicBrainz reintento {}/{} ({}): {}", i, INTENTOS, desc, LogSafe.message(e));
                    dormir(1500L * i);
                } else {
                    log.warn("MusicBrainz agotó reintentos ({}): {}", desc, LogSafe.message(e));
                }
            } finally {
                concurrencyGate.release();
            }
        }
        return Optional.empty();
    }

    private void dormir(long ms) {
        try {
            TimeUnit.MILLISECONDS.sleep(ms);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }
}
