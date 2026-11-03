package emendatium.backend.sources.lyrics;

import emendatium.backend.sources.lrclib.LrcLibClient;
import lombok.RequiredArgsConstructor;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.Optional;

/**
 * Envoltorio sobre LrcLibClient para encajar en la cascada de proveedores.
 * Prefiere la letra sincronizada; si solo hay plano, lo devuelve.
 */
@Component
@Order(2)
@RequiredArgsConstructor
public class LrcLibLyricsProvider implements LyricsProvider {

    private final LrcLibClient client;

    @Override
    public String name() {
        return "LRCLib";
    }

    @Override
    public Optional<String> fetch(String artist, String title, String album, Integer durationSec, Path mp3Path) {
        if (artist == null || title == null || artist.isBlank() || title.isBlank()) {
            return Optional.empty();
        }
        return client.fetch(artist, title, album, durationSec)
                .map(r -> {
                    if (r.syncedLyrics() != null && !r.syncedLyrics().isBlank()) return r.syncedLyrics();
                    if (r.plainLyrics() != null && !r.plainLyrics().isBlank()) return r.plainLyrics();
                    return null;
                })
                .filter(s -> s != null && !s.isBlank());
    }
}
