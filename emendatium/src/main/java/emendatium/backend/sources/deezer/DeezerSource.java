package emendatium.backend.sources.deezer;

import emendatium.backend.shared.TrackMetadata;
import emendatium.backend.sources.EnrichmentContext;
import emendatium.backend.sources.MetadataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Perito Deezer: aporta el BPM autoritativo. Preferencia por ISRC (match
 * exacto); si no hay ISRC, cae a búsqueda por artista + título. Corre después
 * de MusicBrainz y antes del análisis local, para que el BPM real gane sobre
 * el estimado.
 */
@Component
@Order(6)
@RequiredArgsConstructor
public class DeezerSource implements MetadataSource {

    private final DeezerClient client;

    @Override
    public String name() {
        return "Deezer";
    }

    @Override
    public Optional<TrackMetadata> enrich(EnrichmentContext context) {
        Optional<DeezerTrackResponse> track = Optional.empty();

        String isrc = context.soFar().isrc();
        if (isrc != null && !isrc.isBlank()) {
            track = client.lookupByIsrc(isrc);
        }

        // Fallback: búsqueda por artista + título
        if (track.isEmpty()) {
            String artist = context.soFar().artist();
            String title = context.soFar().title();
            if (artist != null && title != null) {
                track = client.searchByArtistTitle(artist, title);
            }
        }

        return track.flatMap(t -> {
            if (t.bpm() == null || t.bpm() < 1) return Optional.empty(); // Deezer usa 0 = sin dato
            return Optional.of(TrackMetadata.builder()
                    .bpm((int) Math.round(t.bpm()))
                    .build());
        });
    }
}
