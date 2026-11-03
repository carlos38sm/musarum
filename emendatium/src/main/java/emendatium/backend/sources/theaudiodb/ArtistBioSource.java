package emendatium.backend.sources.theaudiodb;

import emendatium.backend.shared.TrackMetadata;
import emendatium.backend.shared.Values;
import emendatium.backend.sources.EnrichmentContext;
import emendatium.backend.sources.MetadataSource;
import emendatium.backend.sources.lastfm.LastFmClient;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(5)
@RequiredArgsConstructor
public class ArtistBioSource implements MetadataSource {

    private final TheAudioDbClient audioDbClient;
    private final LastFmClient lastFmClient;

    @Override
    public String name() {
        return "Biografía";
    }

    @Override
    public Optional<TrackMetadata> enrich(EnrichmentContext context) {
        String artistMbid = context.soFar().mbArtistId();
        String artistName = context.soFar().artist();

        Optional<TheAudioDbResponse.Artist> audioDb = audioDbClient.lookupArtistByMbid(artistMbid);

        // Fallback: si el MBID no dio nada, probamos search por nombre
        if (audioDb.isEmpty() && artistName != null && !artistName.isBlank()) {
            audioDb = audioDbClient.searchArtistByName(artistName);
        }

        String biography = audioDb.map(a -> Values.clean(a.biography())).orElse(null);
        if (biography == null && artistName != null && !artistName.isBlank()) {
            biography = lastFmClient.getArtistBio(artistName).map(Values::clean).orElse(null);
        }

        String country = audioDb.map(a -> Values.clean(a.country())).orElse(null);
        String style = audioDb.map(a -> Values.clean(a.style())).orElse(null);
        String website = audioDb.map(a -> Values.clean(a.website())).orElse(null);
        Integer formedYear = audioDb.map(TheAudioDbResponse.Artist::formedYear)
                .filter(y -> y != null && y.matches("\\d{4}"))
                .map(Integer::parseInt)
                .orElse(null);

        if (biography == null && country == null && style == null && website == null && formedYear == null) {
            return Optional.empty();
        }

        return Optional.of(TrackMetadata.builder()
                .artistInfo(new TrackMetadata.ArtistInfo(biography, country, formedYear, style, website))
                .build());
    }
}