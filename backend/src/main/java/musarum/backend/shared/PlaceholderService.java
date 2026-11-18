package musarum.backend.shared;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Stream;

/**
 * Sirve imágenes "placeholder" cuando una canción / álbum / artista /
 * playlist no tiene portada propia. Carlos mete él mismo imágenes
 * relacionadas con música en {@code _placeholders/<tipo>/} dentro del
 * MUSARUM_MUSIC_PATH; esta clase se encarga de elegir una de forma
 * ESTABLE por seed (misma song_id siempre devuelve la misma imagen, para
 * que las portadas placeholder no bailen entre recargas).
 *
 * <p>Si una carpeta está vacía o no existe, devolvemos null y el móvil
 * cae a su cuadro gris de siempre.
 */
@Service
public class PlaceholderService {

    private static final Logger log = LoggerFactory.getLogger(PlaceholderService.class);

    public enum Kind { SONGS, ALBUMS, ARTISTS, PLAYLISTS }

    private final Path root;
    // Cache de listados por carpeta: evita hacer Files.list() en cada
    // request. La carpeta cambia poco (es manual) y cualquier cambio
    // requiere un reinicio del backend — es admisible para este caso.
    private final ConcurrentHashMap<Kind, List<Path>> cache = new ConcurrentHashMap<>();

    public PlaceholderService(@Value("${MUSARUM_MUSIC_PATH}") String musicPath) {
        this.root = Path.of(musicPath, "_placeholders");
    }

    @PostConstruct
    void ensureFolders() {
        try {
            if (!Files.exists(root)) Files.createDirectories(root);
            for (Kind k : Kind.values()) {
                Path sub = root.resolve(k.name().toLowerCase());
                if (!Files.exists(sub)) Files.createDirectories(sub);
            }
            log.info("Placeholders: carpetas listas en {} (mete imágenes y "
                    + "reinicia para que las pille)", root);
        } catch (IOException e) {
            log.warn("No pude preparar _placeholders en {}: {}", root, e.getMessage());
        }
    }

    /**
     * Elige una imagen determinística para esta combinación (tipo, seed).
     * Devuelve {@code null} si no hay imágenes en la carpeta.
     */
    public Path pick(Kind kind, long seed) {
        List<Path> images = listing(kind);
        if (images.isEmpty()) return null;
        // Math.floorMod maneja seeds negativas (hashCode puede serlo).
        int idx = (int) Math.floorMod(seed, (long) images.size());
        return images.get(idx);
    }

    private List<Path> listing(Kind kind) {
        return cache.computeIfAbsent(kind, k -> {
            Path dir = root.resolve(k.name().toLowerCase());
            if (!Files.exists(dir) || !Files.isDirectory(dir)) return List.of();
            try (Stream<Path> s = Files.list(dir)) {
                return s.filter(Files::isRegularFile)
                        .filter(PlaceholderService::isImage)
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList();
            } catch (IOException e) {
                log.warn("No pude listar placeholders de {}: {}", dir, e.getMessage());
                return List.of();
            }
        });
    }

    private static boolean isImage(Path p) {
        String n = p.getFileName().toString().toLowerCase();
        return n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png")
                || n.endsWith(".webp") || n.endsWith(".gif");
    }

    /**
     * Invalida el cache de listados. Expuesto por si en el futuro
     * añadimos un endpoint admin de "refrescar placeholders sin reiniciar".
     */
    public void invalidate() {
        cache.clear();
    }
}
