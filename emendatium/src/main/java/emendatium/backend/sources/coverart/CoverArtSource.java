package emendatium.backend.sources.coverart;

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
@Order(3)
@RequiredArgsConstructor
public class CoverArtSource implements MetadataSource {

    private final CoverArtClient coverArtClient;

    @Override
    public String name() {
        return "CoverArtArchive";
    }

    @Override
    public Optional<TrackMetadata> enrich(EnrichmentContext context) {
        String releaseId = context.soFar().mbReleaseId();
        if (releaseId == null || releaseId.isBlank()) return Optional.empty();

        if (!coverArtClient.coverExists(releaseId)) return Optional.empty();

        return Optional.of(TrackMetadata.builder()
                .coverArt(new TrackMetadata.CoverArt(
                        null,                                   // data = null, no descargamos bytes
                        "image/jpeg",
                        coverArtClient.frontUrl(releaseId),
                        500,
                        500))
                .build());
    }
}
