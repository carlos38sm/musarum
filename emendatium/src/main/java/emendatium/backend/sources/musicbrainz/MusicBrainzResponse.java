package emendatium.backend.sources.musicbrainz;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record MusicBrainzResponse(
        String id,
        String title,
        @JsonProperty("artist-credit") List<ArtistCredit> artistCredit,
        List<String> isrcs,
        List<Release> releases,
        List<Tag> tags,
        List<Genre> genres
) {
    public record ArtistCredit(Artist artist) {}
    public record Artist(String id, String name) {}

    public record Release(
            String id,
            String title,
            String status,
            String date,
            String country,
            String barcode,
            @JsonProperty("artist-credit") List<ArtistCredit> artistCredit,
            @JsonProperty("release-group") ReleaseGroup releaseGroup,
            @JsonProperty("label-info") List<LabelInfo> labelInfo,
            List<Medium> media
    ) {}

    public record ReleaseGroup(
            String id,
            @JsonProperty("primary-type") String primaryType,
            @JsonProperty("secondary-types") List<String> secondaryTypes,
            @JsonProperty("first-release-date") String firstReleaseDate
    ) {}

    public record LabelInfo(
            @JsonProperty("catalog-number") String catalogNumber,
            Label label
    ) {}

    public record Label(String id, String name) {}
    public record Medium(List<Track> tracks) {}
    public record Track(String id, Integer position, String number, RecordingRef recording) {}
    public record RecordingRef(String id) {}
    public record Tag(String name, Integer count) {}
    public record Genre(String name, Integer count) {}
}