package musarum.backend.home.dto;

import musarum.backend.song.dto.SongSummaryDto;

/**
 * Evento del feed "Comunidad" del Home. {@code type} define cómo el móvil
 * lo renderiza (icono, verbo, link al perfil del user):
 *   - "now_playing": X está escuchando esta canción.
 *   - "favorited":   X marcó esta canción como favorita.
 *
 * {@code timestampMs} es el momento en el que ocurrió el evento (para
 * el ordenamiento DESC del feed + "hace Xm"). {@code user} puede ser null
 * si en el futuro añadimos tipos sin autor (nuevas incorporaciones al
 * catálogo, por ejemplo). {@code song} nunca es null — el evento siempre
 * referencia una canción.
 */
public record ActivityEventDto(
        String type,
        long timestampMs,
        UserRef user,
        SongSummaryDto song
) {

    public record UserRef(Long id, String nick, String avatarUrl) {}
}
