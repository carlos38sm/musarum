package emendatium.backend.sources.theaudiodb;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * Respuesta de TheAudioDB artist-mb.php. Sus claves usan el prefijo "str"/"int",
 * así que las mapeamos con @JsonProperty. Solo cogemos lo relevante.
 */
public record TheAudioDbResponse(List<Artist> artists) {

    public record Artist(
            @JsonProperty("strBiographyEN") String biography,
            @JsonProperty("strCountry")     String country,
            @JsonProperty("intFormedYear")  String formedYear,
            @JsonProperty("strStyle")       String style,
            @JsonProperty("strWebsite")     String website
    ) {}
}