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
import java.util.concurrent.atomic.AtomicReference;
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
    // Cache de listados por carpeta con TTL corto (30 s). Antes cacheábamos
    // "para siempre" (hasta reinicio) y aparecía el bug clásico: Carlos
    // arranca el backend con las carpetas vacías (se crean solas en
    // @PostConstruct), el móvil pide un placeholder de inmediato, cacheamos
    // una lista VACÍA, después Carlos mete las imágenes y nunca se refresca
    // hasta que reinicia. Con TTL de 30 s el primer listado "en caliente"
    // tras arrancar se pierde rápido y la carpeta real se ve.
    private static final long TTL_MS = 30_000L;
    @SuppressWarnings("unchecked")
    private final AtomicReference<CachedListing>[] cache = new AtomicReference[Kind.values().length];

    private record CachedListing(long readAtMs, List<Path> images) {}

    public PlaceholderService(@Value("${MUSARUM_MUSIC_PATH}") String musicPath) {
        this.root = Path.of(musicPath, "_placeholders");
        for (int i = 0; i < cache.length; i++) cache[i] = new AtomicReference<>(null);
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
        AtomicReference<CachedListing> slot = cache[kind.ordinal()];
        CachedListing current = slot.get();
        long now = System.currentTimeMillis();
        if (current != null && (now - current.readAtMs) < TTL_MS) {
            return current.images;
        }
        Path dir = root.resolve(kind.name().toLowerCase());
        List<Path> fresh;
        if (!Files.exists(dir) || !Files.isDirectory(dir)) {
            fresh = List.of();
        } else {
            try (Stream<Path> s = Files.list(dir)) {
                fresh = s.filter(Files::isRegularFile)
                        .filter(PlaceholderService::isImage)
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .toList();
            } catch (IOException e) {
                log.warn("No pude listar placeholders de {}: {}", dir, e.getMessage());
                fresh = List.of();
            }
        }
        slot.set(new CachedListing(now, fresh));
        return fresh;
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
        for (AtomicReference<CachedListing> slot : cache) slot.set(null);
    }
}
