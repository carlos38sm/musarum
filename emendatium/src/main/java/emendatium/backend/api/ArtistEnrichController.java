package emendatium.backend.api;

import emendatium.backend.shared.TrackMetadata;
import emendatium.backend.shared.Values;
import emendatium.backend.sources.lastfm.LastFmClient;
import emendatium.backend.sources.theaudiodb.TheAudioDbClient;
import emendatium.backend.sources.theaudiodb.TheAudioDbResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Endpoint ligero para re-enriquecer SOLO datos de artista sin tocar un
 * fichero de audio. El backend lo usa desde "Re-enriquecer artistas" en
 * Análisis del servidor: recorre todos los artistas en DB y por cada uno
 * pide aquí la info fresca de TheAudioDB (fallback LastFM para bio). Así
 * no se pagan BPM/FFT/replaygain por cada canción, que es lo caro.
 */
@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class ArtistEnrichController {

    private final TheAudioDbClient audioDbClient;
    private final LastFmClient lastFmClient;

    public record ArtistEnrichRequest(String name, String mbArtistId) {}

    @PostMapping("/enrich/artist")
    public ResponseEntity<TrackMetadata.ArtistInfo> enrichArtist(@RequestBody ArtistEnrichRequest req) {
        if ((req.name() == null || req.name().isBlank())
                && (req.mbArtistId() == null || req.mbArtistId().isBlank())) {
            return ResponseEntity.badRequest().build();
        }

        Optional<TheAudioDbResponse.Artist> audioDb = audioDbClient.lookupArtistByMbid(req.mbArtistId());
        if (audioDb.isEmpty() && req.name() != null && !req.name().isBlank()) {
            audioDb = audioDbClient.searchArtistByName(req.name());
        }

        String biography = audioDb.map(a -> Values.clean(a.biography())).orElse(null);
        if (biography == null && req.name() != null && !req.name().isBlank()) {
            biography = lastFmClient.getArtistBio(req.name()).map(Values::clean).orElse(null);
        }

        String country = audioDb.map(a -> Values.clean(a.country())).orElse(null);
        String style = audioDb.map(a -> Values.clean(a.style())).orElse(null);
        String website = audioDb.map(a -> Values.clean(a.website())).orElse(null);
        Integer formedYear = audioDb.map(TheAudioDbResponse.Artist::formedYear)
                .filter(y -> y != null && y.matches("\\d{4}"))
                .map(Integer::parseInt)
                .orElse(null);
        String imageUrl = audioDb
                .map(a -> {
                    String t = Values.clean(a.thumb());
                    return (t != null && !t.isBlank()) ? t : Values.clean(a.logo());
                })
                .filter(u -> u != null && (u.startsWith("http://") || u.startsWith("https://")))
                .orElse(null);

        if (biography == null && country == null && style == null && website == null
                && formedYear == null && imageUrl == null) {
            return ResponseEntity.noContent().build();
        }

        return ResponseEntity.ok(new TrackMetadata.ArtistInfo(
                biography, country, formedYear, style, website, imageUrl));
    }
}
