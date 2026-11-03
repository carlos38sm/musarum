package musarum.backend.enrichment.lyrics;

import java.nio.file.Path;
import java.util.Optional;
import musarum.backend.enrichment.LrcLibClient;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Adaptador sobre {@link LrcLibClient} para encajar en la cascada de
 * proveedores. Delega todas las estrategias y la verificación de artista
 * en el cliente, que es quien conoce LrcLib a fondo.
 */
@Component
@Order(2)
class LrcLibLyricsProvider implements LyricsProvider {

    private final LrcLibClient client;

    LrcLibLyricsProvider(LrcLibClient client) {
        this.client = client;
    }

    @Override
    public String name() {
        return "LRCLib";
    }

    @Override
    public Optional<String> fetch(String artist, String title, String album, Integer durationSec, Path audioPath) {
        if (artist == null || title == null || artist.isBlank() || title.isBlank()) {
            return Optional.empty();
        }
        return client.fetchLyrics(artist, title, album, durationSec);
    }
}
