package musarum.backend.shared;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Almacén local de vídeos opcionales por canción. El móvil pide
 * {@code /songs/{id}/video} y, si existe un fichero en la carpeta configurada
 * cuyo nombre empieza por el id de la canción (p. ej. {@code 42.mp4}), lo
 * servimos como stream. Si no, 404 y la píldora oculta el modo Vídeo.
 */
@Component
public class VideoStorage {

    private static final Logger log = LoggerFactory.getLogger(VideoStorage.class);

    private static final String[] EXTENSIONS = { ".mp4", ".mkv", ".webm", ".mov", ".m4v" };

    private final Path baseDir;

    public VideoStorage(@Value("${MUSARUM_VIDEOS_PATH:#{null}}") String videosPath,
                        @Value("${MUSARUM_MUSIC_PATH}") String musicPath) {
        String resolved = (videosPath != null && !videosPath.isBlank())
                ? videosPath
                : Path.of(musicPath).resolve("_videos").toString();
        this.baseDir = Path.of(resolved).toAbsolutePath().normalize();
        try {
            Files.createDirectories(this.baseDir);
            log.info("VideoStorage inicializado en: {}", this.baseDir);
        } catch (IOException e) {
            log.warn("No se pudo crear carpeta de vídeos {}: {}", this.baseDir, e.getMessage());
        }
    }

    public boolean hasVideo(Long songId) {
        return findVideo(songId, null).isPresent();
    }

    public boolean hasVideo(Long songId, String audioPath) {
        return findVideo(songId, audioPath).isPresent();
    }

    public Optional<Path> findVideo(Long songId) {
        return findVideo(songId, null);
    }

    /**
     * Busca el vídeo en 3 pasos:
     *   1. {@code {songId}.ext} — la convención preferida.
     *   2. {@code {basenameDelAudio}.ext} — para que el usuario pueda soltar
     *      el fichero con el mismo nombre que el mp3 original.
     *   3. Si el fichero del audio contiene "Artista - Título", también
     *      probamos coincidencia por igualdad case-insensitive de ese
     *      basename contra los ficheros de la carpeta.
     */
    public Optional<Path> findVideo(Long songId, String audioPath) {
        // 1. Por id
        for (String ext : EXTENSIONS) {
            Path candidate = baseDir.resolve(songId + ext);
            if (Files.exists(candidate)) return Optional.of(candidate);
        }
        if (audioPath == null || audioPath.isBlank()) return Optional.empty();

        // 2. Por nombre base del audio
        String basename = extractBasename(audioPath);
        if (basename.isBlank()) return Optional.empty();
        for (String ext : EXTENSIONS) {
            Path candidate = baseDir.resolve(basename + ext);
            if (Files.exists(candidate)) return Optional.of(candidate);
        }

        // 3. Coincidencia case-insensitive en la carpeta
        try (var stream = Files.list(baseDir)) {
            return stream
                    .filter(Files::isRegularFile)
                    .filter(p -> {
                        String n = p.getFileName().toString().toLowerCase();
                        for (String ext : EXTENSIONS) {
                            if (n.endsWith(ext) && n.substring(0, n.length() - ext.length())
                                    .equalsIgnoreCase(basename)) {
                                return true;
                            }
                        }
                        return false;
                    })
                    .findFirst();
        } catch (IOException e) {
            log.debug("No se pudo listar la carpeta de vídeos: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String extractBasename(String audioPath) {
        String fileName = audioPath.replace("\\", "/");
        int slash = fileName.lastIndexOf('/');
        if (slash >= 0) fileName = fileName.substring(slash + 1);
        int dot = fileName.lastIndexOf('.');
        return (dot > 0) ? fileName.substring(0, dot) : fileName;
    }

    public String contentTypeFor(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        if (name.endsWith(".webm")) return "video/webm";
        if (name.endsWith(".mkv"))  return "video/x-matroska";
        if (name.endsWith(".mov"))  return "video/quicktime";
        return "video/mp4";
    }
}
