package emendatium.backend.fingerprint;

import java.util.List;

/**
 * Mapeo de la respuesta JSON de AcoustID (endpoint /v2/lookup con meta=recordings).
 * Solo declaramos lo que usamos; Jackson 3 ignora el resto por defecto.
 */
public record AcoustIdResponse(
        String status,
        List<Result> results
) {
    public record Result(
            String id,
            Double score,
            List<Recording> recordings
    ) {}

    public record Recording(
            String id,
            String title,
            List<Artist> artists
    ) {}

    public record Artist(
            String id,
            String name
    ) {}
}