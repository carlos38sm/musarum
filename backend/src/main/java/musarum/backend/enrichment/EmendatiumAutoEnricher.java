package musarum.backend.enrichment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import musarum.backend.enrichment.dto.EnrichmentResponse;
import musarum.backend.song.Song;
import musarum.backend.song.SongService;

/**
 * Lanza enriquecimiento con Emendatium en segundo plano por cada canción
 * recién importada. No bloquea el scan: usa el pool {@code scannerExecutor}.
 * Si Emendatium no está configurado o falla, no hace nada (silencioso).
 *
 * <p>El resultado se aplica vía {@link BatchResultApplier#applySingle} para
 * reutilizar la misma lógica que el batch (letras, portadas, biografías…).
 */
@Service
public class EmendatiumAutoEnricher {

    private static final Logger log = LoggerFactory.getLogger(EmendatiumAutoEnricher.class);

    private final EmendatiumClient client;
    private final BatchResultApplier applier;
    private final SongService songService;

    EmendatiumAutoEnricher(EmendatiumClient client,
                           BatchResultApplier applier,
                           SongService songService) {
        this.client = client;
        this.applier = applier;
        this.songService = songService;
    }

    /**
     * Sin @Transactional externo: {@link BatchResultApplier#applySingle} y
     * {@link SongService#findEntityById} abren sus propias transacciones.
     * Mezclar @Async + @Transactional externo + @Transactional internos
     * provocaba {@code UnexpectedRollbackException} al commitear la externa.
     */
    @Async("scannerExecutor")
    public void enrichAsync(Long songId, String audioPath) {
        if (!client.isEnabled()) return;
        try {
            EnrichmentResponse resp = client.enrich(audioPath).orElse(null);
            if (resp == null || resp.metadata() == null) return;

            // Pasamos el ID: applier recarga la entidad DENTRO de su tx
            // para que el song esté managed y los cambios persistan.
            applier.applySingle(songId, resp.metadata());
            log.info("Auto-enrich OK: id={} ({})", songId, audioPath);
        } catch (Exception e) {
            log.debug("Auto-enrich fallido para id={}: {}", songId, e.getMessage());
        }
    }
}
