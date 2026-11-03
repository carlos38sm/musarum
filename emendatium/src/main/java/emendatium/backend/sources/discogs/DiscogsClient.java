package emendatium.backend.sources.discogs;

import emendatium.backend.config.EnricherProperties;
import emendatium.backend.shared.LogSafe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

/**
 * Cliente de Discogs. Busca el release por artista + título de álbum y
 * devuelve el mejor resultado. El token va en la cabecera Authorization.
 */
@Slf4j
@Component
public class DiscogsClient {

    private static final String SEARCH_URL = "https://api.discogs.com/database/search";

    private final RestClient http;
    private final String token;

    public DiscogsClient(RestClient enricherRestClient, EnricherProperties props) {
        this.http = enricherRestClient;
        this.token = props.sources().discogs().token();
    }

    public Optional<DiscogsResponse.Result> searchRelease(String artist, String album) {
        if (token == null || token.isBlank()) {
            log.warn("Discogs sin token; se omite.");
            return Optional.empty();
        }
        if (artist == null || artist.isBlank() || album == null || album.isBlank()) {
            return Optional.empty();
        }
        try {
            URI uri = UriComponentsBuilder.fromUriString(SEARCH_URL)
                    .queryParam("type", "release")
                    .queryParam("artist", artist)
                    .queryParam("release_title", album)
                    .queryParam("per_page", 5)
                    .build()
                    .encode()
                    .toUri();

            DiscogsResponse resp = http.get()
                    .uri(uri)
                    .header("Authorization", "Discogs token=" + token)
                    .retrieve()
                    .body(DiscogsResponse.class);

            if (resp == null || resp.results() == null || resp.results().isEmpty()) {
                return Optional.empty();
            }
            return Optional.of(resp.results().get(0));

        } catch (Exception e) {
            log.warn("Fallo consultando Discogs ({} - {}): {}", artist, album, LogSafe.message(e));
            return Optional.empty();
        }
    }
}