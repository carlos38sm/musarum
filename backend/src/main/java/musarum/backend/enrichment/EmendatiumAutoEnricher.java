package musarum.backend.enrichment;

import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
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
 * <p>Política de reubicación del fichero según dónde esté:
 * <ul>
 *   <li><b>Está en {@code _inbox/}</b> (bandeja de entrada, directo o en
 *       {@code _inbox/sin-identificar/}): si identificamos la canción
 *       la MOVEMOS a la raíz de la biblioteca con nombre bonito; si no,
 *       la movemos a {@code _inbox/sin-identificar/}.</li>
 *   <li><b>Fuera de {@code _inbox/}</b> (ya organizada por el OWNER a
 *       mano, p. ej. canciones viejas): solo renombramos in-place si
 *       hay sugerencia nueva. Jamás la movemos de carpeta sin permiso.</li>
 * </ul>
 */
@Service
public class EmendatiumAutoEnricher {

    private static final Logger log = LoggerFactory.getLogger(EmendatiumAutoEnricher.class);

    private final EmendatiumClient client;
    private final BatchResultApplier applier;
    private final SongService songService;
    private final EnrichmentProgress progress;
    private final Path musicRoot;
    private final Path inboxRoot;

    EmendatiumAutoEnricher(EmendatiumClient client,
                           BatchResultApplier applier,
                           SongService songService,
                           EnrichmentProgress progress,
                           @Value("${MUSARUM_MUSIC_PATH}") String musicPath) {
        this.client = client;
        this.applier = applier;
        this.songService = songService;
        this.progress = progress;
        this.musicRoot = Path.of(musicPath).toAbsolutePath().normalize();
        this.inboxRoot = this.musicRoot.resolve("_inbox");
    }

    /**
     * Sin @Transactional externo: {@link BatchResultApplier#applySingle} y
     * {@link SongService#findEntityById} abren sus propias transacciones.
     */
    @Async("scannerExecutor")
    public void enrichAsync(Long songId, String audioPath) {
        if (!client.isEnabled()) {
            progress.onDone(false);
            return;
        }
        if (progress.isCancelled()) {
            progress.onDone(false);
            return;
        }
        boolean ok = false;
        try {
            boolean inInbox = isInInbox(audioPath);
            EnrichmentResponse resp = client.enrich(audioPath).orElse(null);
            if (resp == null || resp.metadata() == null) {
                // No match. Si está en _inbox (directo), lo mandamos a
                // cuarentena para que la raíz quede limpia. Si ya está en
                // sin-identificar/ no lo movemos (ya está donde toca).
                if (inInbox && !isInQuarantine(audioPath)) {
                    String newPath = client.quarantine(audioPath);
                    if (!newPath.equals(audioPath)) {
                        songService.updateAudioPath(songId, newPath);
                    }
                }
                songService.markEnrichment(songId, EnrichStatus.FAILED_NO_MATCH);
                log.info("Emendatium NO MATCH: id={} ({})", songId, audioPath);
                return;
            }
            ok = true;

            var m = resp.metadata();
            log.info("Emendatium OK: id={} bpm={} key={} rg={} mbArtist={} mbRelease={} cover={}",
                    songId, m.bpm(), m.musicalKey(), m.replayGainTrackGain(),
                    m.mbArtistId(), m.mbReleaseId(),
                    m.coverArt() != null ? "yes" : "no");

            applier.applySingle(songId, m);

            // Sugerencia de nombre: siempre que venga, la usamos. La
            // operación concreta depende de dónde viva el fichero.
            if (resp.suggestions() != null && resp.suggestions().fileName() != null) {
                String newPath;
                if (inInbox) {
                    // Sacamos de _inbox a la raíz de la biblioteca.
                    newPath = client.organize(audioPath, resp.suggestions().fileName());
                } else {
                    // Ya organizada por el OWNER: rename in-place, nada
                    // más. No sacamos de su carpeta.
                    newPath = client.rename(audioPath, resp.suggestions().fileName());
                }
                if (!newPath.equals(audioPath)) {
                    songService.updateAudioPath(songId, newPath);
                }
            }
            songService.markEnrichment(songId, EnrichStatus.OK);
        } catch (Exception e) {
            songService.markEnrichment(songId, EnrichStatus.FAILED_TRANSIENT);
            log.warn("Auto-enrich fallido (transient) id={}: {}", songId, e.getMessage());
        } finally {
            progress.onDone(ok);
        }
    }

    /**
     * ¿El fichero está en la bandeja de entrada (incluida la subcarpeta
     * {@code sin-identificar/})? Normaliza el path para evitar falsos
     * negativos por {@code ../} o barras mixtas.
     */
    private boolean isInInbox(String audioPath) {
        try {
            Path p = Path.of(audioPath).toAbsolutePath().normalize();
            return p.startsWith(inboxRoot);
        } catch (Exception e) {
            return false;
        }
    }

    private boolean isInQuarantine(String audioPath) {
        try {
            Path p = Path.of(audioPath).toAbsolutePath().normalize();
            return p.startsWith(inboxRoot.resolve("sin-identificar"));
        } catch (Exception e) {
            return false;
        }
    }
}
