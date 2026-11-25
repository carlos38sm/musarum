package musarum.backend.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * Builder común para respuestas de imágenes servidas desde disco (portadas
 * de álbum/canción, imagen de artista, avatar de usuario). Centraliza dos
 * cosas que antes vivían duplicadas en 4 controllers:
 * <p>
 * <b>Cache-Control público 7d</b> — Coil/OkHttp cachean el fichero por URL
 * durante una semana sin volver a pedirlo. El cache-busting cuando una
 * imagen cambia lo hace el emisor del URL (ej. SongMapper añade {@code ?v=ts}).
 * <p>
 * <b>ETag débil basado en {@code lastModifiedMs-size}</b> — si el cliente
 * envía {@code If-None-Match} y coincide con el ETag actual del fichero,
 * devolvemos {@code 304 Not Modified} sin body. La llamada típica desde
 * OkHttp (al expirar el max-age) baja de ~80 KB a 0 KB por imagen. Para
 * una pantalla con 20 covers son 1,6 MB menos y la mitad de latencia.
 * <p>
 * Usamos weak-etag ({@code W/"…"}) porque el servidor puede re-tocar el
 * fichero (ej. reescritura de APIC tras enriquecer) y no garantizamos
 * byte-a-byte idéntico — solo semánticamente equivalente.
 */
public final class ImageResponseBuilder {

    private ImageResponseBuilder() {}

    public static ResponseEntity<Resource> build(Path file, String contentType, String ifNoneMatch) {
        try {
            long size = Files.size(file);
            long lastModifiedMs = Files.getLastModifiedTime(file).toMillis();
            String etag = "W/\"" + lastModifiedMs + "-" + size + "\"";

            // Mantenemos Cache-Control en la 304 para que el cliente renueve
            // su max-age sin tener que volver a pedir en 7 días aunque nada
            // haya cambiado.
            if (etag.equals(ifNoneMatch)) {
                return ResponseEntity.status(304)
                        .eTag(etag)
                        .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                        .build();
            }

            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(size)
                    .eTag(etag)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                    .body(new FileSystemResource(file));
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }
}
