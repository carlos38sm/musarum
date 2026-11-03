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
 * Almacén local de fotos de perfil. Hermana de CoverStorage; vive en
 * {@code _profilepictures/} dentro de la carpeta paraguas (MUSARUM_MUSIC_PATH)
 * para que al añadirla al volumen de Docker, Musarum la vea sin más config.
 *
 * URL pública devuelta: {@code /users/{id}/avatar}. El móvil prepone la base.
 */
@Component
public class AvatarStorage {

    private static final Logger log = LoggerFactory.getLogger(AvatarStorage.class);

    private final Path baseDir;

    public AvatarStorage(@Value("${MUSARUM_AVATARS_PATH:#{null}}") String avatarsPath,
                         @Value("${MUSARUM_MUSIC_PATH}") String musicPath) {
        String resolved = (avatarsPath != null && !avatarsPath.isBlank())
                ? avatarsPath
                : Path.of(musicPath).resolve("_profilepictures").toString();
        this.baseDir = Path.of(resolved).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
            log.info("AvatarStorage inicializado en: {}", this.baseDir);
        } catch (IOException e) {
            log.warn("No se pudo crear carpeta de avatares {}: {}", this.baseDir, e.getMessage());
        }
    }

    /**
     * Guarda el avatar y devuelve la URL relativa con cache-busting por
     * timestamp. Dos subidas consecutivas devuelven URLs DISTINTAS aunque
     * apunten al mismo endpoint físico, lo que fuerza a Coil (y a cualquier
     * caché HTTP intermedia) a re-descargar. Antes el updated_at podía no
     * cambiar si Hibernate no detectaba cambio de columna (el string era
     * siempre "/users/{id}/avatar") y Coil servía la imagen vieja.
     */
    public String saveAvatar(Long userId, byte[] bytes, String mimeType) {
        long version = System.currentTimeMillis();
        return save("user-" + userId, bytes, mimeType)
                .map(ignored -> "/users/" + userId + "/avatar?v=" + version)
                .orElse(null);
    }

    public Optional<Path> findAvatar(Long userId) {
        return findFirst("user-" + userId);
    }

    /** Elimina avatar del disco (todas las extensiones). Idempotente. */
    public void deleteAvatar(Long userId) {
        for (String ext : EXTENSIONS) {
            runCatchingDelete(baseDir.resolve("user-" + userId + ext));
        }
    }

    public String contentTypeFor(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".png"))  return "image/png";
        if (name.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
    }

    private Optional<Path> save(String baseName, byte[] bytes, String mimeType) {
        try {
            String ext = extensionFor(mimeType);
            // Borra variantes anteriores (si subió jpg y ahora png, no se mezclan)
            for (String old : EXTENSIONS) {
                if (!old.equals(ext)) runCatchingDelete(baseDir.resolve(baseName + old));
            }
            Path target = baseDir.resolve(baseName + ext);
            Path tmp = baseDir.resolve(baseName + ".tmp");
            Files.write(tmp, bytes);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return Optional.of(target);
        } catch (IOException e) {
            log.warn("No se pudo guardar avatar {}: {}", baseName, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Path> findFirst(String baseName) {
        for (String ext : EXTENSIONS) {
            Path candidate = baseDir.resolve(baseName + ext);
            if (Files.exists(candidate)) return Optional.of(candidate);
        }
        return Optional.empty();
    }

    private String extensionFor(String mimeType) {
        if (mimeType == null) return ".jpg";
        return switch (mimeType.toLowerCase()) {
            case "image/png"  -> ".png";
            case "image/webp" -> ".webp";
            default -> ".jpg";
        };
    }

    private void runCatchingDelete(Path p) {
        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
    }

    private static final String[] EXTENSIONS = {".jpg", ".jpeg", ".png", ".webp"};
}
