package emendatium.backend.sources;

import emendatium.backend.shared.TrackMetadata;

import java.util.Optional;

/**
 * Contrato común de toda fuente de enriquecimiento. Cada fuente examina el
 * contexto disponible y devuelve lo que sabe, sin pisar lo que otras aporten.
 * Añadir una fuente nueva = crear una clase que implemente esto.
 */
public interface MetadataSource {

    /** Nombre corto para logs y trazabilidad de qué fuente aportó cada dato. */
    String name();

    /** Enriquece a partir del contexto disponible (MBID, ISRC, artista+título...). */
    Optional<TrackMetadata> enrich(EnrichmentContext context);
}