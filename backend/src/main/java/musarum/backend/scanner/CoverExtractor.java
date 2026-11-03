package musarum.backend.scanner;

import java.io.File;
import java.util.Optional;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Extrae la portada embebida (APIC/PICTURE) de un archivo de audio usando
 * jaudiotagger. Devuelve los bytes brutos + mimeType detectado. Si el fichero
 * no trae portada, {@code Optional.empty()}.
 */
@Component
public class CoverExtractor {

    private static final Logger log = LoggerFactory.getLogger(CoverExtractor.class);

    public record CoverData(byte[] bytes, String mimeType) {}

    public Optional<CoverData> extract(String audioPath) {
        try {
            File file = new File(audioPath);
            AudioFile audio = AudioFileIO.read(file);
            Tag tag = audio.getTag();
            if (tag == null) return Optional.empty();

            Artwork art = tag.getFirstArtwork();
            if (art == null) return Optional.empty();

            byte[] data = art.getBinaryData();
            if (data == null || data.length == 0) return Optional.empty();

            String mime = art.getMimeType();
            if (mime == null || mime.isBlank()) mime = "image/jpeg";

            return Optional.of(new CoverData(data, mime));
        } catch (Exception e) {
            log.debug("Sin portada embebida en {}: {}", audioPath, e.getMessage());
            return Optional.empty();
        }
    }
}
