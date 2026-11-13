package musarum.backend.home.dto;

/**
 * Mini-perfil de un usuario para la sección "Gustos de la casa". Resume lo
 * que más escucha en la ventana reciente. Cualquiera de los tres bloques de
 * datos puede ser null si el usuario no ha reproducido nada en ese rango.
 * Un usuario con {@code shareTaste=false} no aparece aquí.
 */
public record TasteProfileDto(
        Long userId,
        String nick,
        String avatarUrl,
        /** Nº de reproducciones en la ventana semanal (7 días). */
        long playsThisWeek,
        /** Artista más escuchado esta semana. */
        TopArtist topArtist,
        /** Género dominante esta semana (nombre + conteo). */
        TopGenre topGenre,
        /** Canción más escuchada esta semana. */
        TopSong topSong
) {
    public record TopArtist(Long id, String name, String imageUrl, long plays) {}
    public record TopGenre(Long id, String name, long plays) {}
    public record TopSong(Long id, String title, String coverUrl, String artistsLabel, long plays) {}
}
