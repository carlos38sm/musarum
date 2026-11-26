package emendatium.backend.naming;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Mueve ficheros a la carpeta global de la biblioteca cuando Emendatium
 * los identifica. A diferencia de {@link FileRenamer} (que renombra
 * in-place dentro de la misma carpeta), este componente SACA el fichero
 * de {@code _inbox/} (o de {@code _inbox/sin-identificar/}) y lo deposita
 * en la raíz de la biblioteca con el nombre bonito.
 *
 * <p>Diseño plano: Carlos organiza su biblioteca como una única carpeta
 * sin subdirectorios por artista o álbum — todas las canciones conviven
 * en la raíz. Este Organizer respeta eso.
 *
 * <p>Separado de {@link FileRenamer} a propósito: NO queremos aplicar
 * esta lógica a ficheros que ya están en la raíz de la biblioteca o en
 * cualquier subcarpeta que el usuario haya creado a mano. Solo el
 * backend decide cuándo llamar a {@link #moveToRoot}, y lo hace tras
 * detectar que el fichero vive en la bandeja de entrada.
 */
@Slf4j
@Component
public class FileOrganizer {

    /** Nombre reservado dentro de {@code _inbox/} donde van las que
     *  Emendatium no pudo identificar. El OWNER las verá en una pantalla
     *  dedicada del móvil y les editará los tags para reintentar. */
    public static final String QUARANTINE_SUBDIR = "sin-identificar";

    private final Path musicRoot;
    private final FilenameSanitizer sanitizer;

    public FileOrganizer(@Value("${enricher.folders.input}") String musicRootPath,
                         FilenameSanitizer sanitizer) {
        this.musicRoot = Path.of(musicRootPath).toAbsolutePath().normalize();
        this.sanitizer = sanitizer;
    }

    /**
     * Mueve el fichero desde su ubicación actual (típicamente dentro de
     * {@code _inbox/}) a la raíz de la biblioteca con el nombre indicado.
     * Si en la raíz ya existe un fichero con ese nombre, añade sufijo
     * " (2)", " (3)"...
     *
     * @return el nuevo path absoluto. Si por cualquier motivo el move
     *         falla, devuelve el path original para que el caller sepa
     *         que no hubo cambio y pueda marcar la canción adecuadamente.
     */
    public Path moveToRoot(Path origin, String newNameWithoutExt) {
        if (newNameWithoutExt == null || newNameWithoutExt.isBlank()) {
            log.warn("moveToRoot: newName vacío para {}", origin);
            return origin;
        }
        String ext = extensionOf(origin.getFileName().toString());
        // Saneamos el nombre: por si vinieran chars inválidos en Windows
        // o nombres demasiado largos. Reutilizamos el sanitizer del
        // FilenameBuilder para que las reglas sean idénticas.
        String safeName = sanitizer.sanitize(newNameWithoutExt, 240);
        Path target = uniqueTarget(musicRoot, safeName, ext);
        try {
            Files.createDirectories(musicRoot);
            Files.move(origin, target, StandardCopyOption.ATOMIC_MOVE);
            log.info("📦 Movido a raíz: '{}' → '{}'", origin, target);
            return target;
        } catch (IOException atomicFail) {
            // ATOMIC_MOVE puede fallar si origen y destino están en
            // volúmenes distintos (raro con Docker + bind mount único,
            // pero por si acaso). Fallback a move no-atómico.
            try {
                Files.move(origin, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("📦 Movido a raíz (no atómico): '{}' → '{}'", origin, target);
                return target;
            } catch (IOException e) {
                log.warn("No se pudo mover {} a raíz: {}", origin, e.getMessage());
                return origin;
            }
        }
    }

    /**
     * Mueve el fichero a la carpeta de cuarentena ({@code _inbox/sin-identificar/}).
     * Preserva el nombre original porque todavía no tenemos metadata
     * fiable — el OWNER lo verá tal cual lo subió.
     *
     * <p>Idempotente: si el fichero ya está en la carpeta de cuarentena,
     * devuelve su path actual sin tocar nada.
     */
    public Path moveToQuarantine(Path origin) {
        Path quarantine = musicRoot.resolve("_inbox").resolve(QUARANTINE_SUBDIR);
        if (origin.getParent() != null && origin.getParent().equals(quarantine)) {
            return origin; // ya está en cuarentena
        }
        String originalName = origin.getFileName().toString();
        // Cuarentena preserva la extensión y el nombre tal cual, pero por
        // seguridad lo pasamos por el sanitizer con un maxLen generoso.
        String ext = extensionOf(originalName);
        String base = stripExtension(originalName);
        String safeBase = sanitizer.sanitize(base, 240);
        Path target = uniqueTarget(quarantine, safeBase, ext);
        try {
            Files.createDirectories(quarantine);
            Files.move(origin, target, StandardCopyOption.ATOMIC_MOVE);
            log.info("🗂️ Movido a cuarentena: '{}' → '{}'", origin, target);
            return target;
        } catch (IOException atomicFail) {
            try {
                Files.move(origin, target, StandardCopyOption.REPLACE_EXISTING);
                log.info("🗂️ Movido a cuarentena (no atómico): '{}' → '{}'", origin, target);
                return target;
            } catch (IOException e) {
                log.warn("No se pudo mover {} a cuarentena: {}", origin, e.getMessage());
                return origin;
            }
        }
    }

    public Path musicRoot() {
        return musicRoot;
    }

    private Path uniqueTarget(Path dir, String base, String ext) {
        Path candidate = dir.resolve(base + ext);
        if (!Files.exists(candidate)) return candidate;
        int n = 2;
        while (Files.exists(dir.resolve(base + " (" + n + ")" + ext))) n++;
        return dir.resolve(base + " (" + n + ")" + ext);
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(dot) : "";
    }
}
