package emendatium.backend.sources.theaudiodb;

import emendatium.backend.config.EnricherProperties;
import emendatium.backend.shared.LogSafe;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Optional;

@Slf4j
@Component
public class TheAudioDbClient {

    private static final String BASE = "https://www.theaudiodb.com/api/v1/json/";

    private final RestClient http;
    private final String apiKey;

    public TheAudioDbClient(RestClient enricherRestClient, EnricherProperties props) {
        this.http = enricherRestClient;
        this.apiKey = props.sources().theaudiodb().apiKey();
    }

    @Cacheable(value = "artistBio", unless = "#result == null")
    public Optional<TheAudioDbResponse.Artist> lookupArtistByMbid(String artistMbid) {
        if (artistMbid == null || artistMbid.isBlank()) return Optional.empty();
        try {
            String url = BASE + apiKey + "/artist-mb.php?i=" + artistMbid;
            TheAudioDbResponse resp = http.get().uri(url).retrieve().body(TheAudioDbResponse.class);
            if (resp == null || resp.artists() == null || resp.artists().isEmpty()) return Optional.empty();
            return Optional.of(resp.artists().get(0));
        } catch (Exception e) {
            log.info("TheAudioDB sin datos para artista {}: {}", artistMbid, LogSafe.message(e));
            return Optional.empty();
        }
    }

    /** Búsqueda por nombre, cuando no tenemos MBID del artista. */
    @Cacheable(value = "artistBioByName", unless = "#result == null")
    public Optional<TheAudioDbResponse.Artist> searchArtistByName(String artistName) {
        if (artistName == null || artistName.isBlank()) return Optional.empty();
        try {
            String url = UriComponentsBuilder.fromUriString(BASE + apiKey + "/search.php")
                    .queryParam("s", artistName)
                    .build()
                    .toUriString();

            TheAudioDbResponse resp = http.get().uri(url).retrieve().body(TheAudioDbResponse.class);
            if (resp == null || resp.artists() == null || resp.artists().isEmpty()) return Optional.empty();
            return Optional.of(resp.artists().get(0));
        } catch (Exception e) {
            log.info("TheAudioDB search sin datos para '{}': {}", artistName, LogSafe.message(e));
            return Optional.empty();
        }
    }
}