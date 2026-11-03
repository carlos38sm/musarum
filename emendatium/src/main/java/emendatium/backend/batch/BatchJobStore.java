package emendatium.backend.batch;

import emendatium.backend.config.EnricherProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Persistencia del estado de los lotes. Cada {@link BatchJob} tiene un fichero
 * JSON hermano del .jsonl de resultados ({@code batch_<id>.status.json}) que
 * se reescribe en cada hito (arranque, cada N procesados, final o cancelación).
 *
 * <p>Al arrancar se cargan todos los que haya, de forma que un GET
 * /api/enrich/batch/{id} siga funcionando aunque el servidor se haya reiniciado.
 * Un lote que se quedó a medio procesar (status PROCESSING) se marca FAILED,
 * porque su hilo murió con el proceso.
 */
@Slf4j
@Component
public class BatchJobStore {

    private static final String SUFFIX = ".status.json";

    private final Path dir;
    private final ObjectMapper mapper;

    public BatchJobStore(EnricherProperties props, ObjectMapper mapper) {
        this.dir = Path.of(props.folders().batchOutput()).toAbsolutePath().normalize();
        this.mapper = mapper;
        try { Files.createDirectories(dir); } catch (IOException ignored) {}
    }

    public record Snapshot(
            String id,
            String folder,
            int totalFiles,
            String outputFile,
            Instant startedAt,
            BatchJob.Status status,
            int processed,
            int failed,
            String lastError,
            Instant finishedAt
    ) {}

    public void save(BatchJob job) {
        Path file = statusFile(job.id());
        Snapshot snap = new Snapshot(
                job.id(),
                job.folder() != null ? job.folder().toString() : null,
                job.totalFiles(),
                job.outputFile() != null ? job.outputFile().toString() : null,
                job.startedAt(),
                job.status(),
                job.processed(),
                job.failed(),
                job.lastError(),
                job.finishedAt());
        try {
            Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
            Files.writeString(tmp, mapper.writeValueAsString(snap));
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            log.warn("No se pudo persistir estado del lote {}: {}", job.id(), e.getMessage());
        }
    }

    /** Carga todos los snapshots. Un PROCESSING huérfano se degrada a FAILED. */
    public List<BatchJob> loadAll() {
        List<BatchJob> jobs = new ArrayList<>();
        if (!Files.isDirectory(dir)) return jobs;

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + SUFFIX)) {
            for (Path f : stream) {
                try {
                    Snapshot s = mapper.readValue(Files.readAllBytes(f), Snapshot.class);
                    BatchJob job = toJob(s);
                    if (job != null) jobs.add(job);
                } catch (Exception e) {
                    log.warn("Estado de lote ilegible '{}': {}", f.getFileName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warn("No se pudo listar el directorio de lotes {}: {}", dir, e.getMessage());
        }
        return jobs;
    }

    private BatchJob toJob(Snapshot s) {
        if (s == null || s.id() == null) return null;
        BatchJob job = new BatchJob(
                s.id(),
                s.folder() != null ? Path.of(s.folder()) : null,
                s.totalFiles(),
                s.outputFile() != null ? Path.of(s.outputFile()) : null,
                s.startedAt());

        for (int i = 0; i < s.processed(); i++) job.incrementProcessed();
        for (int i = 0; i < s.failed(); i++)    job.incrementFailed();
        job.setLastError(s.lastError());

        BatchJob.Status restaurada = s.status();
        if (restaurada == BatchJob.Status.PROCESSING || restaurada == BatchJob.Status.QUEUED) {
            // El proceso murió a mitad del lote: no lo relanzamos solo.
            job.setLastError("Interrumpido por reinicio del servicio");
            job.finish(BatchJob.Status.FAILED);
        } else {
            if (s.finishedAt() != null) {
                job.setStatus(restaurada);
            } else {
                job.setStatus(restaurada);
            }
        }
        return job;
    }

    private Path statusFile(String id) {
        return dir.resolve("batch_" + id + SUFFIX);
    }
}
