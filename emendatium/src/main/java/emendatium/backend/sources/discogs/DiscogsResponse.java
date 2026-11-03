package emendatium.backend.sources.discogs;

import java.util.List;

/**
 * Mapeo del JSON de búsqueda de Discogs (/database/search). El propio
 * resultado ya trae sello, nº de catálogo, año, país, código de barras y estilos.
 */
public record DiscogsResponse(List<Result> results) {

    public record Result(
            Long id,
            String title,
            String year,
            String country,
            String catno,
            List<String> label,
            List<String> barcode,
            List<String> genre,
            List<String> style
    ) {}
}