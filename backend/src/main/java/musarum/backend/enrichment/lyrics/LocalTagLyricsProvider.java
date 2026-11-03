package musarum.backend.enrichment.lyrics;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Lee la letra directamente del tag del fichero (frame USLT en MP3, LYRICS
 * en FLAC/Vorbis). Es la fuente más rápida y la que NUNCA tiene problemas
 * de matching: si el que preparó el fichero metió letra, es LA correcta.
 *
 * Suele ser texto plano. El cliente mobile lo renderiza en modo no
 * sincronizado cuando no detecta marcas [mm:ss].
 */
@Component
@Order(1)
public class LocalTagLyricsProvider implements LyricsProvider {

    private static final Logger log = LoggerFactory.getLogger(LocalTagLyricsProvider.class);

    @Override
    public String name() {
        return "LocalTag";
    }

    @Override
    public Optional<String> fetch(String artist, String title, String album, Integer durationSec, Path audioPath) {
        if (audioPath == null || !Files.exists(audioPath)) {
            return Optional.empty();
        }
        try {
            AudioFile audio = AudioFileIO.read(audioPath.toFile());
            Tag tag = audio.getTag();
            if (tag == null) return Optional.empty();
            String lyrics = tag.getFirst(FieldKey.LYRICS);
            if (lyrics == null || lyrics.isBlank()) return Optional.empty();
            log.info("  ✓ LocalTag: letra encontrada en tag ({} chars)", lyrics.length());
            return Optional.of(lyrics);
        } catch (Exception e) {
            log.debug("  LocalTag: no pude leer el tag de {}: {}", audioPath.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }
}
