package musarum.backend.enrichment.lyrics;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Fuente individual de letras. El coordinador ({@code LyricsLookupService})
 * prueba las implementaciones en orden (@Order) hasta que una devuelve algo.
 *
 * El resultado puede ser sincronizado (LRC con [mm:ss.xx]) o plano: el cliente
 * mobile sabe parsear ambos — si no detecta timestamps, lo trata como letra
 * no sincronizada.
 */
public interface LyricsProvider {

    /** Nombre corto para logs y trazabilidad de qué proveedor aportó la letra. */
    String name();

    /**
     * Busca la letra para la pista. Los parámetros pueden ser null salvo
     * artist/title para la mayoría de proveedores — cada uno comprueba lo
     * que necesita.
     */
    Optional<String> fetch(String artist, String title, String album, Integer durationSec, Path audioPath);
}
