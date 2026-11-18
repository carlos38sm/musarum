package musarum.backend.shared;

import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

/**
 * Entrega imágenes placeholder cuando una canción / álbum / artista /
 * playlist no tiene portada. El cliente llama a {@code
 * GET /placeholders/{kind}/{seed}} y el backend elige una imagen fija para
 * esa combinación (determinista). Si no hay imágenes en la carpeta,
 * devolvemos 404 y el móvil deja su cuadro gris.
 */
@RestController
@RequestMapping("/placeholders")
class PlaceholderController {

    private final PlaceholderService service;

    PlaceholderController(PlaceholderService service) {
        this.service = service;
    }

    @GetMapping("/{kind}/{seed}")
    ResponseEntity<FileSystemResource> random(
            @PathVariable String kind,
            @PathVariable long seed) {
        PlaceholderService.Kind k = parseKind(kind);
        if (k == null) return ResponseEntity.notFound().build();
        Path image = service.pick(k, seed);
        if (image == null || !Files.exists(image)) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                // Cache fuerte: la elección es estable por seed, así que el
                // cliente puede cachear agresivamente. 7 días es suficiente
                // para que si Carlos cambia las imágenes y reinicia el
                // backend, el móvil lo pille pronto de todas formas.
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic())
                .contentType(mediaTypeFor(image))
                .body(new FileSystemResource(image));
    }

    private PlaceholderService.Kind parseKind(String raw) {
        try {
            return PlaceholderService.Kind.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private MediaType mediaTypeFor(Path p) {
        String n = p.getFileName().toString().toLowerCase(Locale.ROOT);
        if (n.endsWith(".png")) return MediaType.IMAGE_PNG;
        if (n.endsWith(".webp")) return MediaType.valueOf("image/webp");
        if (n.endsWith(".gif")) return MediaType.IMAGE_GIF;
        return MediaType.IMAGE_JPEG;
    }
}
