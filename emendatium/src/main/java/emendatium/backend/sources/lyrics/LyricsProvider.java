package emendatium.backend.sources.lyrics;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Fuente individual de letras. El coordinador (LyricsSource) prueba las
 * implementaciones en orden hasta que una devuelve algo utilizable.
 *
 * El resultado puede ser sincronizado (LRC con [mm:ss.xx]) o texto plano:
 * el cliente mobile sabe parsear ambos — si no detecta timestamps, lo trata
 * como letra sin sincronizar.
 */
public interface LyricsProvider {

    /** Nombre corto para logs y trazabilidad de qué proveedor aportó la letra. */
    String name();

    /**
     * Busca la letra para la pista. Devuelve el texto (LRC o plano) o vacío.
     * Los parámetros pueden ser null salvo artist/title para la mayoría de
     * los proveedores — cada uno comprueba lo que necesita.
     */
    Optional<String> fetch(String artist, String title, String album, Integer durationSec, Path mp3Path);
}
