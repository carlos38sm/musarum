package emendatium.backend.analysis;

import emendatium.backend.shared.TrackMetadata;
import emendatium.backend.sources.EnrichmentContext;
import emendatium.backend.sources.MetadataSource;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@Order(0)
@RequiredArgsConstructor
public class TechnicalAnalysisSource implements MetadataSource {

    private final AudioProbe probe;

    @Override
    public String name() {
        return "Técnico";
    }

    @Override
    public Optional<TrackMetadata> enrich(EnrichmentContext context) {
        if (context.mp3Path() == null) return Optional.empty();
        return probe.probe(context.mp3Path()).map(p -> TrackMetadata.builder()
                .durationMs(p.durationMs())
                .bitrateKbps(p.bitrateKbps())
                .sampleRateHz(p.sampleRateHz())
                .vbr(p.vbr())
                .build());
    }
}