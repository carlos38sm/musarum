package emendatium.backend.fingerprint;

import emendatium.backend.config.EnricherProperties;
import emendatium.backend.shared.LogSafe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Cliente de AcoustID: huella → MBID de la grabación. Solo acepta coincidencias
 * con score suficiente; por debajo del umbral, preferimos no identificar (vacío).
 */
@Slf4j
@Component
public class AcoustIdClient {

    private static final String LOOKUP_URL = "https://api.acoustid.org/v2/lookup";
    private static final double MIN_SCORE = 0.5;

    private final RestClient http;
    private final String apiKey;

    public AcoustIdClient(RestClient enricherRestClient, EnricherProperties props) {
        this.http = enricherRestClient;
        this.apiKey = props.sources().acoustid().apiKey();
    }

    public Optional<AcoustIdMatch> lookup(ChromaprintClient.Fingerprint fp) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("AcoustID sin API key; se omite el fingerprint.");
            return Optional.empty();
        }
        try {
            MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
            form.add("client", apiKey);
            form.add("duration", String.valueOf(fp.durationSeconds()));
            form.add("fingerprint", fp.fingerprint());
            form.add("meta", "recordings");

            AcoustIdResponse resp = http.post()
                    .uri(LOOKUP_URL)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(form)
                    .retrieve()
                    .body(AcoustIdResponse.class);

            if (resp == null || !"ok".equals(resp.status()) || resp.results() == null) {
                return Optional.empty();
            }

            // Para cada result válido elegimos la mejor grabación interna
            // (más metadatos útiles). Luego, entre results, el que tenga
            // mejor score — y, a igualdad, el que lleve más metadatos.
            return resp.results().stream()
                    .filter(r -> r.recordings() != null && !r.recordings().isEmpty())
                    .filter(r -> (r.score() != null ? r.score() : 0.0) >= MIN_SCORE)
                    .map(r -> toMatch(r, bestRecording(r.recordings())))
                    .max(Comparator
                            .comparingDouble(AcoustIdMatch::score)
                            .thenComparingInt(this::richnessScore));

        } catch (Exception e) {
            log.warn("Fallo consultando AcoustID: {}", LogSafe.message(e));
            return Optional.empty();
        }
    }

    /** Elige, dentro de un result, la grabación con más metadatos útiles. */
    private AcoustIdResponse.Recording bestRecording(List<AcoustIdResponse.Recording> recordings) {
        return recordings.stream()
                .max(Comparator.comparingInt(this::recordingRichness))
                .orElse(recordings.get(0));
    }

    /** Puntúa cuántos campos útiles trae la grabación. Mayor = mejor. */
    private int recordingRichness(AcoustIdResponse.Recording rec) {
        int score = 0;
        if (rec.title() != null && !rec.title().isBlank()) score += 2;
        if (rec.artists() != null && !rec.artists().isEmpty()) {
            score += 2;
            AcoustIdResponse.Artist first = rec.artists().get(0);
            if (first.name() != null && !first.name().isBlank()) score += 1;
            if (first.id() != null && !first.id().isBlank())     score += 1;
        }
        return score;
    }

    private int richnessScore(AcoustIdMatch m) {
        int s = 0;
        if (m.title()  != null && !m.title().isBlank())  s += 2;
        if (m.artist() != null && !m.artist().isBlank()) s += 2;
        if (m.artistMbid() != null && !m.artistMbid().isBlank()) s += 1;
        return s;
    }

    private AcoustIdMatch toMatch(AcoustIdResponse.Result result, AcoustIdResponse.Recording rec) {
        List<AcoustIdResponse.Artist> artists = rec.artists();
        boolean hayArtista = artists != null && !artists.isEmpty();
        String artistName = hayArtista ? artists.get(0).name() : null;
        String artistMbid = hayArtista ? artists.get(0).id() : null;
        double score = result.score() != null ? result.score() : 0.0;
        return new AcoustIdMatch(rec.id(), artistMbid, rec.title(), artistName, score);
    }
}