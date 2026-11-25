package emendatium.backend.analysis;

import emendatium.backend.config.EnricherProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

/**
 * Decodifica el MP3 a PCM mono usando ffmpeg. Devuelve un array de floats
 * normalizados en [-1, 1], listos para el análisis matemático.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioDecoder {

    private final EnricherProperties props;

    // Límite de audio a decodificar: BpmDetector lee 40s, KeyDetector 60s.
    // Decodificar más es tirar tiempo y RAM, especialmente para mixes DJ de
    // 30+ min donde pasábamos de ~2s a ~15s por canción. Con -t 60 ffmpeg
    // corta tras un minuto decodificado — más rápido y menos huella.
    private static final int DECODE_SECONDS = 60;
    // Timeout de proceso: ffmpeg sobre un fichero corrupto puede quedarse
    // atascado en bucle. Con 60s decodificando a 22kHz tiene de sobra.
    private static final int PROCESS_TIMEOUT_SECONDS = 90;

    public Optional<float[]> decodeToPcm(Path mp3) {
        Process proc = null;
        try {
            int sr = props.analysis().sampleRate();
            ProcessBuilder pb = new ProcessBuilder(
                    props.binaries().ffmpeg(),
                    "-y", "-i", mp3.toString(),
                    // Corta la decodificación al minuto — suficiente para
                    // BPM (40s) y Key (60s). Si en el futuro el análisis
                    // quisiera usar más, subir también este límite.
                    "-t", String.valueOf(DECODE_SECONDS),
                    "-f", "s16le",
                    "-ar", String.valueOf(sr),
                    "-ac", "1",
                    "pipe:1");
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            proc = pb.start();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = proc.getInputStream()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            }
            // waitFor con timeout: sin este, ffmpeg atascado dejaba el thread
            // bloqueado para siempre y el proceso quedaba huérfano. Fijamos
            // un techo razonable y, si lo supera, matamos el proceso.
            if (!proc.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                proc.destroyForcibly();
                log.warn("ffmpeg excedió el tiempo decodificando {}", mp3.getFileName());
                return Optional.empty();
            }

            byte[] pcm = baos.toByteArray();
            if (pcm.length < 44100) return Optional.empty();

            float[] samples = new float[pcm.length / 2];
            ByteBuffer bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < samples.length; i++) samples[i] = bb.getShort() / 32768.0f;
            return Optional.of(samples);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Decodificación interrumpida de {}", mp3.getFileName());
            return Optional.empty();
        } catch (Exception e) {
            log.warn("No se pudo decodificar {} con ffmpeg: {}", mp3.getFileName(), e.getMessage());
            return Optional.empty();
        } finally {
            // Garantiza que no quedan procesos ffmpeg vivos si saltó algo
            // antes del waitFor (p. ej. InterruptedException leyendo el pipe).
            if (proc != null && proc.isAlive()) {
                proc.destroyForcibly();
            }
        }
    }
}