package musarum.backend.home.dto;

/**
 * Metadatos de una lista inteligente generada al vuelo. No se persiste
 * nada: la lista nace cada vez que el móvil llama a /home/smart/{slug}.
 *
 * {@code iconHint} es un token semántico ("party", "focus", "undiscovered",
 * "forgotten") — el móvil lo mapea a su propio icono/color para no
 * acoplar presentación al backend.
 */
public record SmartPlaylistInfoDto(
        String slug,
        String title,
        String subtitle,
        String iconHint
) {}
