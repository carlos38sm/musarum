package musarum.backend.song.dto;

/**
 * Patch de tags ID3 + metadatos JPA de una canción. Todos los campos
 * nullable: el móvil envía solo los que cambia. Si {@code renameFile=true}
 * el backend intenta renombrar el fichero en disco a
 * "{artist} - {title}.{ext}" dentro de la misma carpeta y actualiza
 * {@code audio_path}. Un fallo del rename NO revierte los tags — queda
 * documentado en el log, el OWNER puede reintentar.
 */
public record TagEditRequest(
        String title,
        String artist,
        String album,
        String genre,
        Integer year,
        Integer trackNumber,
        Boolean renameFile
) {}
