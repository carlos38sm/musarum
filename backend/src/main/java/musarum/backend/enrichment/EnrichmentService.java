package musarum.backend.enrichment;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import musarum.backend.enrichment.dto.BatchJobStatus;
import musarum.backend.enrichment.dto.EnrichmentResponse;
import musarum.backend.song.Song;
import musarum.backend.song.SongService;

@Service
public class EnrichmentService {

    private static final Logger log = LoggerFactory.getLogger(EnrichmentService.class);
    private static final int POLL_INTERVAL_MS = 15_000;
    private static final List<String> TERMINAL_STATUSES = List.of("COMPLETED", "FAILED", "CANCELLED");

    private final EnrichmentClient enrichmentClient;
    private final SongService songService;
    private final BatchResultApplier batchResultApplier;
    private final String musicFolder;

    EnrichmentService(EnrichmentClient enrichmentClient,
                      SongService songService,
                      BatchResultApplier batchResultApplier,
                      @Value("${MUSARUM_MUSIC_PATH}") String musicFolder) {
        this.enrichmentClient = enrichmentClient;
        this.songService = songService;
        this.batchResultApplier = batchResultApplier;
        this.musicFolder = musicFolder;
    }

    public void enrichSong(Long songId) {
        Song song = songService.findEntityById(songId);
        EnrichmentResponse response = enrichmentClient.enrich(song.getAudioPath());
        // Pasamos ID: applier recarga la entidad en su propia transacción
        // (song aquí sería detached cuando pasa el límite transaccional).
        batchResultApplier.applySingle(songId, response.metadata());
        log.info("Canción {} enriquecida individualmente", songId);
    }

    @Async("scannerExecutor")
    public void runBatchEnrichment() {
        log.info("Batch de enriquecimiento iniciado en hilo: {}", Thread.currentThread().getName());

        BatchJobStatus status = enrichmentClient.startBatch(musicFolder);
        String jobId = status.jobId();
        log.info("Batch arrancado, jobId={}, totalFiles={}", jobId, status.totalFiles());

        while (!TERMINAL_STATUSES.contains(status.status())) {
            sleep(POLL_INTERVAL_MS);
            status = enrichmentClient.getBatchStatus(jobId);
            log.info("Progreso batch {}: {}/{} procesadas, {} fallidas",
                    jobId, status.processed(), status.totalFiles(), status.failed());
        }

        if (!"COMPLETED".equals(status.status())) {
            log.warn("Batch {} terminó con estado {}", jobId, status.status());
            return;
        }

        batchResultApplier.apply(status.outputFile());
    }

    public void reapplyBatch(String outputFile) {
        batchResultApplier.apply(outputFile);
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}