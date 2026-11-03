package emendatium.backend.naming;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Optional;

@Slf4j
@Component
public class FileRenamer {

    public Path renameInPlace(Path original, String nuevoNombreSinExt) {
        if (nuevoNombreSinExt == null || nuevoNombreSinExt.isBlank()) return original;

        String actualName = original.getFileName().toString();
        String ext = extensionOf(actualName);
        String actualBase = stripExtension(actualName);
        if (nuevoNombreSinExt.equals(actualBase)) {
            return original;
        }

        Path destino = resolveDestino(original, nuevoNombreSinExt, ext);

        if (!destino.getParent().equals(original.getParent())) {
            log.error("🚫 Renombrado abortado: el destino saldría de la carpeta original");
            return original;
        }

        try {
            String nombreOriginal = original.getFileName().toString();
            String nombreNuevo = destino.getFileName().toString();

            Files.move(original, destino, StandardCopyOption.ATOMIC_MOVE);
            log.info("📝 Renombrado: '{}' → '{}'", nombreOriginal, nombreNuevo);

            return destino;

        } catch (IOException e) {
            log.warn("No se pudo renombrar '{}': {}", original.getFileName(), e.getMessage());
            return original;
        }
    }

    private Path resolveDestino(Path original, String nuevoNombreSinExt, String ext) {
        Path parent = original.getParent();
        Path candidato = parent.resolve(nuevoNombreSinExt + ext);

        if (candidato.equals(original)) return candidato;
        if (!Files.exists(candidato)) return candidato;

        int n = 2;
        while (Files.exists(parent.resolve(nuevoNombreSinExt + " (" + n + ")" + ext))) n++;
        return parent.resolve(nuevoNombreSinExt + " (" + n + ")" + ext);
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(0, dot) : name;
    }

    /** Devuelve la extensión con el punto incluido (".mp3") o cadena vacía si no tiene. */
    private String extensionOf(String name) {
        int dot = name.lastIndexOf('.');
        return (dot > 0) ? name.substring(dot) : "";
    }

    public Optional<String> suggestNewName(Path original, String nuevoNombreSinExt) {
        if (nuevoNombreSinExt == null || nuevoNombreSinExt.isBlank()) return Optional.empty();
        String actualName = original.getFileName().toString();
        String actualBase = stripExtension(actualName);
        if (nuevoNombreSinExt.equals(actualBase)) return Optional.empty();
        return Optional.of(nuevoNombreSinExt + extensionOf(actualName));
    }
}
