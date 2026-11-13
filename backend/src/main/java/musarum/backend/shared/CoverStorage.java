package musarum.backend.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Almacén local de portadas e imágenes de artista. Guarda bytes como ficheros
 * en una carpeta configurable ({@code MUSARUM_COVERS_PATH}) y devuelve la URL
 * relativa que el móvil usará para pedirlas: {@code /albums/{id}/cover} o
 * {@code /artists/{id}/image}. El mobile prepone la base URL del backend.
 */
@Component
public class CoverStorage {

    private static final Logger log = LoggerFactory.getLogger(CoverStorage.class);

    private final Path baseDir;

    public CoverStorage(@Value("${MUSARUM_COVERS_PATH:#{null}}") String coversPath,
                        @Value("${MUSARUM_MUSIC_PATH}") String musicPath) {
        // Default: hermana del folder de música, llamada _covers
        String resolved = (coversPath != null && !coversPath.isBlank())
                ? coversPath
                : Path.of(musicPath).resolve("_covers").toString();
        this.baseDir = Path.of(resolved).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
            log.info("CoverStorage inicializado en: {}", this.baseDir);
        } catch (IOException e) {
            log.warn("No se pudo crear carpeta de portadas {}: {}", this.baseDir, e.getMessage());
        }
    }

    /** Guarda la portada de un álbum y devuelve la URL relativa pública. */
    public String saveAlbumCover(Long albumId, byte[] bytes, String mimeType) {
        return save("album-" + albumId, bytes, mimeType)
                .map(ignored -> "/albums/" + albumId + "/cover")
                .orElse(null);
    }

    /** Guarda la imagen de un artista y devuelve la URL relativa pública. */
    public String saveArtistImage(Long artistId, byte[] bytes, String mimeType) {
        return save("artist-" + artistId, bytes, mimeType)
                .map(ignored -> "/artists/" + artistId + "/image")
                .orElse(null);
    }

    /** Devuelve el fichero físico para servirlo, si existe. */
    public Optional<Path> findAlbumCover(Long albumId) {
        return findFirst("album-" + albumId);
    }

    public Optional<Path> findArtistImage(Long artistId) {
        return findFirst("artist-" + artistId);
    }

    /** Borra todas las variantes (.jpg/.png/.webp) de un cover. Idempotente. */
    public void deleteAlbumCover(Long albumId) {
        deleteAll("album-" + albumId);
    }

    public void deleteArtistImage(Long artistId) {
        deleteAll("artist-" + artistId);
    }

    private void deleteAll(String baseName) {
        for (String ext : new String[]{".jpg", ".jpeg", ".png", ".webp"}) {
            Path candidate = baseDir.resolve(baseName + ext);
            try { Files.deleteIfExists(candidate); } catch (IOException ignored) {}
        }
    }

    private Optional<Path> save(String baseName, byte[] bytes, String mimeType) {
        try {
            String ext = extensionFor(mimeType);
            Path target = baseDir.resolve(baseName + ext);
            Path tmp = baseDir.resolve(baseName + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return Optional.of(target);
        } catch (IOException e) {
            log.warn("No se pudo guardar portada {}: {}", baseName, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Path> findFirst(String baseName) {
        for (String ext : new String[]{".jpg", ".jpeg", ".png", ".webp"}) {
            Path candidate = baseDir.resolve(baseName + ext);
            if (Files.exists(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    public String contentTypeFor(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private String extensionFor(String mimeType) {
        if (mimeType == null) return ".jpg";
        return switch (mimeType.toLowerCase()) {
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }
}
