package emendatium.backend.sources.deezer;

import java.util.List;

/**
 * Respuesta de Deezer. Dos formas:
 *   - Lookup por ISRC: campos top-level (id, title, bpm, ...).
 *   - Search:          lista "data" con los resultados.
 * Si no encuentra, Deezer devuelve un objeto "error".
 */
public record DeezerTrackResponse(
        Long id,
        String title,
        Double bpm,
        Double gain,
        String isrc,
        List<DeezerTrackResponse> data,
        Integer total,
        DeezerError error
) {
    public record DeezerError(String type, String message, Integer code) {}
}
