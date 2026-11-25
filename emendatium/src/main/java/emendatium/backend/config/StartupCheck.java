package emendatium.backend.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

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
        Process p = null;
        try {
            ProcessBuilder pb = new ProcessBuilder(nombre, "-version");
            pb.redirectErrorStream(true);
            p = pb.start();
            // Timeout en arranque: si por lo que sea el binario pide stdin
            // o se cuelga, no queremos retrasar la app entera esperando. 5s
            // son mucho más de lo que tarda `ffmpeg -version` normalmente.
            if (!p.waitFor(5, TimeUnit.SECONDS)) {
                p.destroyForcibly();
                log.warn("  ⚠ Binario '{}' no respondió a -version en 5s", etiqueta);
                return;
            }
            // Drain del stdout para no dejar buffers sin cerrar.
            try (var is = p.getInputStream()) { is.readAllBytes(); }
            log.info("  ✓ Binario '{}' encontrado", etiqueta);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("  ⚠ Comprobación de '{}' interrumpida", etiqueta);
        } catch (Exception e) {
            log.warn("  ⚠ Binario '{}' no encontrado en PATH: {}", etiqueta, e.getMessage());
        } finally {
            if (p != null && p.isAlive()) p.destroyForcibly();
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