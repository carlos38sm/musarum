package emendatium.backend.sources.lrclib;

import com.fasterxml.jackson.annotation.JsonProperty;

public record LrcLibResponse(
        Integer id,
        @JsonProperty("trackName")   String trackName,
        @JsonProperty("artistName")  String artistName,
        @JsonProperty("albumName")   String albumName,
        Integer duration,
        @JsonProperty("syncedLyrics") String syncedLyrics,
        @JsonProperty("plainLyrics")  String plainLyrics
) {}