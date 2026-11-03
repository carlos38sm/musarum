package emendatium.backend.sources.lrclib;

import emendatium.backend.shared.TrackMetadata;
import emendatium.backend.sources.EnrichmentContext;
import emendatium.backend.sources.MetadataSource;
import emendatium.backend.sources.lyrics.LyricsProvider;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * MetadataSource de letras. Coordina los distintos LyricsProvider en orden
 * (@Order): LocalTag → LRCLib → NetEase. Se queda con la primera que
 * devuelva algo utilizable.
 *
 * El campo donde se almacena la letra en TrackMetadata se llama
 * {@code syncedLyrics} por historia, pero puede contener texto plano: el
 * cliente mobile sabe parsear ambos (LrcParser devuelve lista vacía si no
 * encuentra timestamps → el UI pinta modo texto).
 */
@Slf4j
@Component
@Order(4)
public class LyricsSource implements MetadataSource {

    private final List<LyricsProvider> providers;

    public LyricsSource(List<LyricsProvider> providers) {
        this.providers = providers;
        log.info("LyricsSource activo con providers: {}",
                providers.stream().map(LyricsProvider::name).toList());
    }

    @Override
    public String name() {
        return "Lyrics";
    }

    @Override
    public Optional<TrackMetadata> enrich(EnrichmentContext context) {
        String artist = context.soFar().artist();
        String title  = context.soFar().title();
        String album  = context.soFar().album();
        Integer durationSec = context.durationSec();

        if (isBlank(artist) || isBlank(title)) {
            return Optional.empty();
        }

        for (LyricsProvider provider : providers) {
            Optional<String> result = provider.fetch(artist, title, album, durationSec, context.mp3Path());
            if (result.isPresent() && !result.get().isBlank()) {
                log.info("    🎵 Letra aportada por {}", provider.name());
                return Optional.of(TrackMetadata.builder()
                        .syncedLyrics(result.get())
                        .build());
            }
        }
        log.info("    ✗ Sin letra tras {} proveedores", providers.size());
        return Optional.empty();
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
