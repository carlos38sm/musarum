package emendatium.backend.sources.lastfm;

/** Respuesta de Last.fm artist.getInfo. Solo nos interesa la biografía. */
public record LastFmArtistResponse(Artist artist) {
    public record Artist(Bio bio) {}
    public record Bio(String summary) {}
}