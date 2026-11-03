package emendatium.backend.sources.lastfm;

import emendatium.backend.config.EnricherProperties;
import emendatium.backend.shared.LogSafe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.Optional;

/**
 * Cliente de Last.fm para biografía de artista, como respaldo de TheAudioDB
 * (cobertura distinta; a veces uno tiene bio y el otro no).
 */
@Slf4j
@Component
public class LastFmClient {

    private static final String URL = "https://ws.audioscrobbler.com/2.0/";

    private final RestClient http;
    private final String apiKey;

    public LastFmClient(RestClient enricherRestClient, EnricherProperties props) {
        this.http = enricherRestClient;
        this.apiKey = props.sources().lastfm().apiKey();
    }

    @Cacheable(value = "lastfmBio", unless = "#result == null")
    public Optional<String> getArtistBio(String artistName) {
        if (apiKey == null || apiKey.isBlank()) return Optional.empty();
        if (artistName == null || artistName.isBlank()) return Optional.empty();
        try {
            URI uri = UriComponentsBuilder.fromUriString(URL)
                    .queryParam("method", "artist.getinfo")
                    .queryParam("artist", artistName)
                    .queryParam("api_key", apiKey)
                    .queryParam("format", "json")
                    .queryParam("autocorrect", "1")
                    .build().encode().toUri();

            LastFmArtistResponse resp = http.get().uri(uri).retrieve().body(LastFmArtistResponse.class);
            if (resp == null || resp.artist() == null || resp.artist().bio() == null) {
                return Optional.empty();
            }
            String summary = resp.artist().bio().summary();
            if (summary == null || summary.isBlank()) return Optional.empty();

            // Last.fm añade un enlace "Read more on Last.fm" al final; lo recortamos
            String limpio = summary.replaceAll("(?s)\\s*<a href=\"https://www\\.last\\.fm.*$", "").trim();
            return Optional.of(limpio);

        } catch (Exception e) {
            log.info("Last.fm sin biografía para '{}': {}", artistName, LogSafe.message(e));
            return Optional.empty();
        }
    }
}