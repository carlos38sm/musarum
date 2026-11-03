package emendatium.backend.analysis;

import emendatium.backend.config.EnricherProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Calcula ReplayGain analizando el volumen con ffmpeg (filtro ebur128).
 * Devuelve ganancia (dB) y pico (0..1). Estándar EBU R 128.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ReplayGainAnalyzer {

    private static final Pattern LOUDNESS = Pattern.compile("I:\\s*(-?[0-9]+(?:\\.[0-9]+)?)\\s*LUFS");
    private static final Pattern PEAK = Pattern.compile("Peak:\\s*(-?[0-9]+(?:\\.[0-9]+)?)\\s*dBFS");

    private final EnricherProperties props;

    public record Result(Float trackGain, Float trackPeak) {}

    public Optional<Result> analyze(Path mp3) {
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    props.binaries().ffmpeg(),
                    "-nostats", "-hide_banner",
                    "-i", mp3.toString(),
                    "-af", "ebur128=peak=true",
                    "-f", "null", "-");
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            Float loudness = null;
            Float peakDbfs = null;
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    Matcher lm = LOUDNESS.matcher(line);
                    if (lm.find()) loudness = Float.parseFloat(lm.group(1));
                    Matcher pm = PEAK.matcher(line);
                    if (pm.find()) peakDbfs = Float.parseFloat(pm.group(1));
                }
            }
            proc.waitFor();
            if (loudness == null) return Optional.empty();

            // ReplayGain = objetivo (-18 LUFS) - loudness medido
            float trackGain = -18.0f - loudness;
            // Peak lineal 0..1 desde dBFS
            Float trackPeak = (peakDbfs != null) ? (float) Math.pow(10, peakDbfs / 20.0) : null;

            return Optional.of(new Result(trackGain, trackPeak));

        } catch (Exception e) {
            log.warn("ReplayGain falló sobre {}: {}", mp3.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }
}