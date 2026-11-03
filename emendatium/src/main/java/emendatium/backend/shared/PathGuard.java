package emendatium.backend.shared;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * Validador de rutas: comprueba que un archivo/carpeta existe y es accesible.
 * NO confina a carpeta permitida — eso es responsabilidad de Musarum.
 */
@Slf4j
@Component
public class PathGuard {

    /** Extensiones de audio soportadas para enriquecimiento y batch. */
    public static final Set<String> AUDIO_EXTENSIONS = Set.of(
            ".mp3", ".flac", ".m4a", ".ogg", ".oga", ".opus", ".wav");

    public static class InvalidPathException extends RuntimeException {
        public InvalidPathException(String message) { super(message); }
    }

    /** Valida un archivo de audio: existe, extensión reconocida, es archivo regular. */
    public Path validateAudio(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) throw new InvalidPathException("La ruta está vacía.");

        Path path;
        try {
            path = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new InvalidPathException("Ruta con formato inválido: " + rawPath);
        }

        if (!hasAudioExtension(path)) {
            throw new InvalidPathException("El archivo no es un formato soportado: " + path.getFileName());
        }
        if (!Files.exists(path)) throw new InvalidPathException("El archivo no existe: " + path);
        if (!Files.isRegularFile(path)) throw new InvalidPathException("La ruta no es un archivo: " + path);

        return path;
    }

    /** @deprecated usar {@link #validateAudio} que acepta cualquier formato soportado. */
    @Deprecated
    public Path validateMp3(String rawPath) {
        return validateAudio(rawPath);
    }

    /** Valida una carpeta: existe, es directorio, es accesible. */
    public Path validateFolder(String rawPath) {
        if (rawPath == null || rawPath.isBlank()) throw new InvalidPathException("La ruta de carpeta está vacía.");

        Path path;
        try {
            path = Path.of(rawPath).toAbsolutePath().normalize();
        } catch (Exception e) {
            throw new InvalidPathException("Ruta de carpeta inválida: " + rawPath);
        }

        if (!Files.exists(path) || !Files.isDirectory(path)) {
            throw new InvalidPathException("La carpeta no existe: " + path);
        }

        return path;
    }

    /** Escanea recursivamente una carpeta y devuelve todos los audios encontrados. */
    public List<Path> findAudioFiles(Path folder) {
        try (Stream<Path> walk = Files.walk(folder)) {
            return walk
                    .filter(Files::isRegularFile)
                    .filter(this::hasAudioExtension)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new InvalidPathException("No se pudo escanear la carpeta: " + e.getMessage());
        }
    }

    /** @deprecated usar {@link #findAudioFiles} para incluir todos los formatos. */
    @Deprecated
    public List<Path> findMp3s(Path folder) {
        return findAudioFiles(folder);
    }

    public boolean hasAudioExtension(Path path) {
        String name = path.getFileName().toString().toLowerCase();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return false;
        return AUDIO_EXTENSIONS.contains(name.substring(dot));
    }
}
