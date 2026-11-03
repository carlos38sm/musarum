package emendatium.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Comprueba al arrancar que los binarios externos existen y que las
 * carpetas de trabajo son accesibles.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StartupCheck implements CommandLineRunner {

    private final EnricherProperties props;

    @Override
    public void run(String... args) {
        log.info("🔍 Comprobaciones de arranque...");

        comprobarBinario(props.binaries().ffmpeg(),  "ffmpeg");
        comprobarBinario(props.binaries().fpcalc(),  "fpcalc");
        comprobarCarpeta(props.folders().batchOutput(), "batch-output");

        log.info("✅ Comprobaciones completadas.");
    }

    private void comprobarBinario(String nombre, String etiqueta) {
        try {
            ProcessBuilder pb = new ProcessBuilder(nombre, "-version");
            pb.redirectErrorStream(true);
            Process p = pb.start();
            p.waitFor();
            log.info("  ✓ Binario '{}' encontrado", etiqueta);
        } catch (Exception e) {
            log.warn("  ⚠ Binario '{}' no encontrado en PATH: {}", etiqueta, e.getMessage());
        }
    }

    private void comprobarCarpeta(String ruta, String etiqueta) {
        if (ruta == null || ruta.isBlank()) {
            log.warn("  ⚠ Carpeta '{}' no configurada", etiqueta);
            return;
        }
        try {
            Path path = Path.of(ruta);
            Files.createDirectories(path);
            log.info("  ✓ Carpeta '{}' accesible: {}", etiqueta, path.toAbsolutePath());
        } catch (Exception e) {
            log.warn("  ⚠ Carpeta '{}' no accesible ({}): {}", etiqueta, ruta, e.getMessage());
        }
    }
}