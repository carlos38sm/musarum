package emendatium.backend.sources.lyrics;

import lombok.extern.slf4j.Slf4j;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Optional;

/**
 * Lee la letra directamente del tag del fichero (frame USLT en MP3, LYRICS en
 * FLAC/Vorbis). Es la fuente más rápida y la que NUNCA tiene problemas de
 * matching: si el que preparó el fichero metió letra, es la correcta.
 *
 * Suele ser plain lyrics (sin timestamps). El cliente mobile lo renderiza
 * en modo texto cuando no detecta marcas [mm:ss].
 */
@Slf4j
@Component
@Order(1)
public class LocalTagLyricsProvider implements LyricsProvider {

    @Override
    public String name() {
        return "LocalTag";
    }

    @Override
    public Optional<String> fetch(String artist, String title, String album, Integer durationSec, Path mp3Path) {
        if (mp3Path == null || !Files.exists(mp3Path)) {
            return Optional.empty();
        }
        try {
            AudioFile audio = AudioFileIO.read(mp3Path.toFile());
            Tag tag = audio.getTag();
            if (tag == null) return Optional.empty();

            String lyrics = tag.getFirst(FieldKey.LYRICS);
            if (lyrics == null || lyrics.isBlank()) {
                log.debug("    LocalTag: sin letra en el fichero {}", mp3Path.getFileName());
                return Optional.empty();
            }
            log.info("    ✓ LocalTag: letra encontrada en tag ({} chars)", lyrics.length());
            return Optional.of(lyrics);
        } catch (Exception e) {
            log.debug("    LocalTag: no pude leer el tag ({}): {}", mp3Path.getFileName(), e.getMessage());
            return Optional.empty();
        }
    }
}
