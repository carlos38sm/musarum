package emendatium.backend.sources;

import emendatium.backend.shared.TrackMetadata;
import java.nio.file.Path;

public record EnrichmentContext(
        String recordingMbid,
        String fallbackArtist,
        String fallbackTitle,
        Integer durationSec,
        Path mp3Path,
        TrackMetadata soFar
) {}