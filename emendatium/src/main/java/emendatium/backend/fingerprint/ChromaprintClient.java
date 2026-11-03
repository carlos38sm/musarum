package emendatium.backend.fingerprint;

import emendatium.backend.config.EnricherProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Envuelve el binario `fpcalc` (Chromaprint) para generar la huella
 * acústica de un MP3. Es una llamada a proceso externo, como ffmpeg.
 */
@Slf4j
@Component
public class ChromaprintClient {

    private final String fpcalcPath;

    public ChromaprintClient(EnricherProperties props) {
        this.fpcalcPath = props.binaries().fpcalc();
    }

    /** La huella tal cual la produce fpcalc: cadena + duración en segundos. */
    public record Fingerprint(String fingerprint, int durationSeconds) {}

    public Optional<Fingerprint> fingerprint(Path mp3) {
        try {
            ProcessBuilder pb = new ProcessBuilder(fpcalcPath, mp3.toString());
            pb.redirectErrorStream(true); // evita bloqueos si fpcalc escribe en stderr
            Process proc = pb.start();

            String fingerprint = null;
            Integer duration = null;

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    int eq = line.indexOf('=');
                    if (eq < 0) continue; // líneas de error u otras: se ignoran
                    String clave = line.substring(0, eq);
                    String valor = line.substring(eq + 1).trim();
                    if ("FINGERPRINT".equals(clave)) {
                        fingerprint = valor;
                    } else if ("DURATION".equals(clave)) {
                        duration = (int) Math.round(Double.parseDouble(valor));
                    }
                }
            }

            if (!proc.waitFor(30, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                log.warn("fpcalc excedió el tiempo con {}", mp3.getFileName());
                return Optional.empty();
            }
            if (proc.exitValue() != 0) {
                log.warn("fpcalc devolvió código {} para {}", proc.exitValue(), mp3.getFileName());
                return Optional.empty();
            }
            if (fingerprint == null || duration == null) {
                log.warn("fpcalc no produjo huella válida para {}", mp3.getFileName());
                return Optional.empty();
            }
            return Optional.of(new Fingerprint(fingerprint, duration));

        } catch (Exception e) {
            log.warn("No se pudo ejecutar fpcalc sobre {} — ¿está en el PATH? ({})",
                    mp3.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }
}