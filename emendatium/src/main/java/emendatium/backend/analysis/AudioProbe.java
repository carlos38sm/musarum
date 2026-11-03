package emendatium.backend.analysis;

import com.fasterxml.jackson.annotation.JsonProperty;
import emendatium.backend.config.EnricherProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFileIO;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class AudioProbe {

    private final EnricherProperties props;
    private final ObjectMapper mapper;

    public record ProbeResult(Long durationMs, Integer bitrateKbps, Integer sampleRateHz, Boolean vbr) {}

    public Optional<ProbeResult> probe(Path mp3) {
        try {
            String ffprobe = props.binaries().ffmpeg().replaceFirst("(?i)ffmpeg(\\.exe)?$", "ffprobe$1");
            if (ffprobe.equals(props.binaries().ffmpeg())) ffprobe = "ffprobe";

            ProcessBuilder pb = new ProcessBuilder(
                    ffprobe, "-v", "error", "-print_format", "json",
                    "-show_format", "-show_streams", "-select_streams", "a:0",
                    mp3.toString());
            pb.redirectErrorStream(true);
            Process proc = pb.start();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (InputStream is = proc.getInputStream()) {
                is.transferTo(baos);
            }
            proc.waitFor();

            FfprobeOutput out = mapper.readValue(baos.toByteArray(), FfprobeOutput.class);
            if (out == null || out.format() == null) return Optional.empty();

            Long durationMs = out.format().duration() != null
                    ? Math.round(Double.parseDouble(out.format().duration()) * 1000.0) : null;
            Integer bitrateKbps = out.format().bitRate() != null
                    ? Integer.parseInt(out.format().bitRate()) / 1000 : null;
            Integer sampleRateHz = (out.streams() != null && !out.streams().isEmpty()
                    && out.streams().get(0).sampleRate() != null)
                    ? Integer.parseInt(out.streams().get(0).sampleRate()) : null;

            Boolean vbr = leerVbr(mp3);

            return Optional.of(new ProbeResult(durationMs, bitrateKbps, sampleRateHz, vbr));

        } catch (Exception e) {
            log.warn("ffprobe falló sobre {}: {}", mp3.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }

    private Boolean leerVbr(Path mp3) {
        try {
            var header = AudioFileIO.read(mp3.toFile()).getAudioHeader();
            return header.isVariableBitRate();
        } catch (Exception e) {
            return null;
        }
    }

    private record FfprobeOutput(Format format, List<Stream> streams) {}
    private record Format(String duration, @JsonProperty("bit_rate") String bitRate) {}
    private record Stream(@JsonProperty("sample_rate") String sampleRate) {}
}