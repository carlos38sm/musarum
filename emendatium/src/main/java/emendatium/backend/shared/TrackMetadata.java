package emendatium.backend.shared;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import java.time.LocalDate;
import java.util.List;

@Builder(toBuilder = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record TrackMetadata(
        String title,
        String artist,
        String albumArtist,
        String album,
        String releaseType,
        Integer trackNumber,
        Integer trackTotal,
        Integer discNumber,
        Integer discTotal,
        LocalDate releaseDate,
        Integer year,
        Integer originalYear,
        List<String> genres,
        String composer,
        String label,
        String acoustId,
        String mbRecordingId,
        String mbReleaseId,
        String mbReleaseGroupId,
        String mbArtistId,
        String isrc,
        String barcode,
        String catalogNumber,
        Long discogsReleaseId,
        Long durationMs,
        Integer bitrateKbps,
        Integer sampleRateHz,
        Boolean vbr,
        Integer bpm,
        String musicalKey,
        String mood,
        Float replayGainTrackGain,
        Float replayGainTrackPeak,
        ArtistInfo artistInfo,
        CoverArt coverArt,
        String syncedLyrics  
) {

    public static TrackMetadata empty() {
        return TrackMetadata.builder().build();
    }

    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ArtistInfo(
            String biography,
            String country,
            Integer formedYear,
            String style,
            String website
    ) {}

    @Builder(toBuilder = true)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record CoverArt(
            String data,
            String mimeType,
            String sourceUrl,
            Integer width,
            Integer height
    ) {}
}