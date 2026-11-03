package musarum.backend.enrichment;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import musarum.backend.enrichment.exception.LyricsWriteException;

@Component
public class LyricsWriter {

    private final Path lyricsFolder;

    LyricsWriter(@Value("${MUSARUM_MUSIC_PATH}") String musicPath) {
        this.lyricsFolder = Path.of(musicPath, "_lyrics");
    }

    /** Devuelve el path estándar donde guardaríamos la letra de esta canción. */
    public Path pathFor(Long songId) {
        return lyricsFolder.resolve(songId + ".lrc");
    }

    public String write(Long songId, String syncedLyrics) {
        try {
            if (!Files.exists(lyricsFolder)) Files.createDirectories(lyricsFolder);
            Path lyricsFile = lyricsFolder.resolve(songId + ".lrc");
            Files.writeString(lyricsFile, syncedLyrics, StandardCharsets.UTF_8);
            return lyricsFile.toString().replace("\\", "/");
        } catch (IOException e) {
            throw new LyricsWriteException(songId, e);
        }
    }
}