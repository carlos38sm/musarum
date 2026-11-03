package musarum.backend.shared;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import musarum.backend.album.AlbumService;
import musarum.backend.artist.ArtistService;

/**
 * Al arrancar, normaliza las claves de artistas y álbumes existentes y fusiona
 * los duplicados cuya clave normalizada coincide. Evita que un backend que fue
 * poblado antes de la deduplicación quede con "JAY-Z" y "JAÝ-Z" convivendo.
 */
@Component
public class DedupBootstrap {

    private static final Logger log = LoggerFactory.getLogger(DedupBootstrap.class);

    private final ArtistService artistService;
    private final AlbumService albumService;

    DedupBootstrap(ArtistService artistService, AlbumService albumService) {
        this.artistService = artistService;
        this.albumService = albumService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void dedupOnStartup() {
        try {
            int artists = artistService.dedupeAll();
            int albums = albumService.dedupeAll();
            if (artists > 0 || albums > 0) {
                log.info("Dedup al arranque: {} artistas fusionados, {} álbumes fusionados",
                        artists, albums);
            } else {
                log.info("Dedup al arranque: nada que fusionar");
            }
        } catch (Exception e) {
            log.warn("Dedup al arranque falló: {}", e.getMessage(), e);
        }
    }
}
