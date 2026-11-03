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

/**
 * Decodifica el MP3 a PCM mono usando ffmpeg. Devuelve un array de floats
 * normalizados en [-1, 1], listos para el análisis matemático.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AudioDecoder {

    private final EnricherProperties props;

    public Optional<float[]> decodeToPcm(Path mp3) {
        try {
            int sr = props.analysis().sampleRate();
            ProcessBuilder pb = new ProcessBuilder(
                    props.binaries().ffmpeg(),
                    "-y", "-i", mp3.toString(),
                    "-f", "s16le",
                    "-ar", String.valueOf(sr),
                    "-ac", "1",
                    "pipe:1");
            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process proc = pb.start();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = proc.getInputStream()) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            }
            proc.waitFor();

            byte[] pcm = baos.toByteArray();
            if (pcm.length < 44100) return Optional.empty();

            float[] samples = new float[pcm.length / 2];
            ByteBuffer bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN);
            for (int i = 0; i < samples.length; i++) samples[i] = bb.getShort() / 32768.0f;
            return Optional.of(samples);

        } catch (Exception e) {
            log.warn("No se pudo decodificar {} con ffmpeg: {}", mp3.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }
}