package musarum.backend.enrichment.lyrics;

import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import musarum.backend.enrichment.LrcLibClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Adaptador sobre {@link LrcLibClient} para encajar en la cascada de
 * proveedores. Delega todas las estrategias y la verificación de artista
 * en el cliente, que es quien conoce LrcLib a fondo.
 *
 * <p>Rate limit: Semaphore de 4 permits. Si "Re-enriquecer todo" dispara
 * 7000 enrichAsync a la vez, cada uno puede intentar pedir letras aquí —
 * lrclib.net es gratuito y nos pueden banear por IP si hacemos tormenta.
 * tryAcquire con timeout corto: si no hay hueco, devolvemos empty en vez
 * de bloquear al thread del scanner esperando.
 */
@Component
@Order(2)
class LrcLibLyricsProvider implements LyricsProvider {

    private static final Logger log = LoggerFactory.getLogger(LrcLibLyricsProvider.class);
    private static final Semaphore RATE_LIMIT = new Semaphore(4);

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
        boolean acquired;
        try {
            acquired = RATE_LIMIT.tryAcquire(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        if (!acquired) {
            log.warn("LRCLib rate limit: no hueco en 10s para {} - {}", artist, title);
            return Optional.empty();
        }
        try {
            return client.fetchLyrics(artist, title, album, durationSec);
        } finally {
            RATE_LIMIT.release();
        }
    }
}
