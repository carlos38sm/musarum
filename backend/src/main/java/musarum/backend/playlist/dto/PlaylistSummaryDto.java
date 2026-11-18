package musarum.backend.playlist.dto;

import java.util.List;

/**
 * Resumen de playlist para listados (Biblioteca).
 *
 * @param coverUrls hasta 4 URLs de portada de las primeras canciones de
 *        la playlist (por posición). El cliente las compone en un mosaico
 *        2×2 estilo Spotify. Si la playlist está vacía o esas canciones
 *        no tienen cover, los huecos quedan a null y el cliente pinta un
 *        placeholder.
 */
public record PlaylistSummaryDto(
        Long id,
        String name,
        boolean isPublic,
        int songCount,
        List<String> coverUrls
) {}
