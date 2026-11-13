package musarum.backend.enrichment;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import musarum.backend.enrichment.dto.EnrichmentResponse;
import musarum.backend.song.EnrichStatus;
import musarum.backend.song.SongService;

/**
 * Lanza enriquecimiento con Emendatium en segundo plano por cada canción
 * recién importada. No bloquea el scan: usa el pool {@code scannerExecutor}.
 * Si Emendatium no está configurado, no hace nada (silencioso).
 *
 * <p>El resultado se aplica vía {@link BatchResultApplier#applySingle} para
 * reutilizar la misma lógica que el batch (letras, portadas, biografías…).
 * Cada intento marca {@code enrich_status} en la canción: OK si llegó
 * metadata, FAILED_NO_MATCH si Emendatium no identificó la pista,
 * FAILED_TRANSIENT si fue un error de red/timeout (reintentable).
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
            if (resp == null || resp.metadata() == null) {
                // Emendatium respondió sin metadata usable → sin match.
                songService.markEnrichment(songId, EnrichStatus.FAILED_NO_MATCH);
                log.info("Emendatium NO MATCH: id={} ({})", songId, audioPath);
                return;
            }

            // Log de lo que llega para que el OWNER pueda ver en docker logs
            // por qué BPM/key siguen null aunque Emendatium esté 🟢. Si
            // bpm/key vienen null aquí, el problema está dentro de
            // Emendatium (fpcalc falta, FFT no corre, etc.).
            var m = resp.metadata();
            log.info("Emendatium OK: id={} bpm={} key={} rg={} mbArtist={} mbRelease={} cover={}",
                    songId, m.bpm(), m.musicalKey(), m.replayGainTrackGain(),
                    m.mbArtistId(), m.mbReleaseId(),
                    m.coverArt() != null ? "yes" : "no");

            applier.applySingle(songId, m);
            songService.markEnrichment(songId, EnrichStatus.OK);
        } catch (Exception e) {
            // Red caída, timeout, 5xx → marcamos como transient para que
            // "Re-enriquecer todo" lo re-intente la próxima vez.
            songService.markEnrichment(songId, EnrichStatus.FAILED_TRANSIENT);
            log.warn("Auto-enrich fallido (transient) id={}: {}", songId, e.getMessage());
        }
    }
}
