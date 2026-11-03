package emendatium.backend.analysis;

import emendatium.backend.shared.TrackMetadata;
import emendatium.backend.sources.EnrichmentContext;
import emendatium.backend.sources.MetadataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Perito ReplayGain: calcula ganancia y pico de volumen. Va al final porque
 * es la operación más lenta y solo se lanza si aún no tenemos los datos.
 */
@Component
@Order(11)
@RequiredArgsConstructor
public class ReplayGainSource implements MetadataSource {

    private final ReplayGainAnalyzer analyzer;

    @Override
    public String name() {
        return "ReplayGain";
    }

    @Override
    public Optional<TrackMetadata> enrich(EnrichmentContext context) {
        if (context.soFar().replayGainTrackGain() != null) return Optional.empty();
        if (context.mp3Path() == null) return Optional.empty();
        return analyzer.analyze(context.mp3Path()).map(r -> TrackMetadata.builder()
                .replayGainTrackGain(r.trackGain())
                .replayGainTrackPeak(r.trackPeak())
                .build());
    }
}