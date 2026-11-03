package emendatium.backend.fingerprint;

/**
 * Resultado limpio de identificar una canción: el MBID de la grabación y la
 * confianza (0..1). Título y artista quedan para trazas y desambiguación.
 */
public record AcoustIdMatch(
        String recordingMbid,
        String artistMbid,
        String title,
        String artist,
        double score
) {}