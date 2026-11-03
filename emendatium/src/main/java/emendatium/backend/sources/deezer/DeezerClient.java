package emendatium.backend.sources.deezer;

import emendatium.backend.shared.Values;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Optional;

/**
 * Cliente de Deezer: busca el track por ISRC (coincidencia exacta) y, como
 * fallback, por artista+título. Objetivo principal: obtener el BPM autoritativo.
 * API pública, sin claves. Cacheado por clave de búsqueda.
 */
@Slf4j
@Component
public class DeezerClient {

    private static final String ISRC_URL   = "https://api.deezer.com/track/isrc:";
    private static final String SEARCH_URL = "https://api.deezer.com/search";

    private final RestClient http;

    public DeezerClient(RestClient enricherRestClient) {
        this.http = enricherRestClient;
    }

    @Cacheable(value = "deezerTrack", unless = "#result == null")
    public Optional<DeezerTrackResponse> lookupByIsrc(String isrc) {
        if (Values.clean(isrc) == null) return Optional.empty();
        try {
            String url = ISRC_URL + isrc;
            DeezerTrackResponse resp = http.get().uri(url).retrieve().body(DeezerTrackResponse.class);
            if (resp == null || resp.error() != null || resp.id() == null) {
                return Optional.empty();
            }
            return Optional.of(resp);
        } catch (Exception e) {
            log.info("Deezer sin datos para ISRC {}: {}", isrc, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * Búsqueda por artista + título. Usa el DSL avanzado de Deezer
     * (artist:"..." track:"...") para evitar falsos positivos. Devuelve el
     * primer resultado con BPM > 0, si lo hay.
     */
    @Cacheable(value = "deezerSearch", unless = "#result == null")
    public Optional<DeezerTrackResponse> searchByArtistTitle(String artist, String title) {
        if (Values.clean(artist) == null || Values.clean(title) == null) return Optional.empty();
        try {
            String query = String.format("artist:\"%s\" track:\"%s\"",
                    artist.replace("\"", ""), title.replace("\"", ""));
            String url = UriComponentsBuilder.fromUriString(SEARCH_URL)
                    .queryParam("q", query)
                    .queryParam("limit", 5)
                    .build()
                    .toUriString();

            DeezerTrackResponse resp = http.get().uri(url).retrieve().body(DeezerTrackResponse.class);
            if (resp == null || resp.data() == null || resp.data().isEmpty()) return Optional.empty();

            List<DeezerTrackResponse> candidatos = resp.data();
            // Priorizamos los que traen BPM real
            return candidatos.stream()
                    .filter(t -> t.bpm() != null && t.bpm() > 0)
                    .findFirst()
                    .or(() -> Optional.of(candidatos.get(0)));
        } catch (Exception e) {
            log.info("Deezer sin datos para '{} - {}': {}", artist, title, e.getMessage());
            return Optional.empty();
        }
    }
}