package emendatium.backend.analysis;

import emendatium.backend.config.EnricherProperties;
import emendatium.backend.shared.TrackMetadata;
import emendatium.backend.sources.EnrichmentContext;
import emendatium.backend.sources.MetadataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@Order(10)
@RequiredArgsConstructor
public class LocalAnalysisSource implements MetadataSource {

    private final AudioDecoder decoder;
    private final BpmDetector bpm;
    private final KeyDetector key;
    private final EnricherProperties props;

    @Override
    public String name() {
        return "AnálisisLocal";
    }

    @Override
    public Optional<TrackMetadata> enrich(EnrichmentContext context) {
        boolean necesitaBpm = context.soFar().bpm() == null;
        boolean necesitaKey = context.soFar().musicalKey() == null;
        if (!necesitaBpm && !necesitaKey) return Optional.empty();
        if (context.mp3Path() == null) return Optional.empty();

        Optional<float[]> samples = decoder.decodeToPcm(context.mp3Path());
        if (samples.isEmpty()) {
            log.warn("🔬 Análisis local: no se pudo decodificar {}", context.mp3Path().getFileName());
            return Optional.empty();
        }

        int sr = props.analysis().sampleRate();
        Integer detectedBpm = necesitaBpm ? bpm.detect(samples.get(), sr) : null;
        String detectedKey = necesitaKey ? key.detect(samples.get(), sr) : null;
        log.info("🔬 Análisis local: BPM={} Key={}", detectedBpm, detectedKey);

        if (detectedBpm == null && detectedKey == null) return Optional.empty();
        return Optional.of(TrackMetadata.builder()
                .bpm(detectedBpm)
                .musicalKey(detectedKey)
                .build());
    }
}