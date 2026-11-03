package emendatium.backend.batch;

import emendatium.backend.config.EnricherProperties;
import emendatium.backend.enrichment.EnrichmentService;
import emendatium.backend.enrichment.EnrichmentService.EnrichmentResult;
import emendatium.backend.shared.PathGuard;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servicio de enriquecimiento por lotes. Procesa todos los archivos de audio
 * de una carpeta en un hilo de fondo, escribiendo resultados línea a línea en
 * un archivo JSONL. El estado se persiste tras cada hito, de modo que un GET
 * del jobId sigue respondiendo aunque el servicio se haya reiniciado.
 */
@Slf4j
@Service
public class BatchService {

    private static final int PERSIST_EVERY_N = 10;

    private final EnrichmentService enrichmentService;
    private final PathGuard pathGuard;
    private final EnricherProperties props;
    private final ObjectMapper mapper;
    private final BatchJobStore store;

    private final ConcurrentHashMap<String, BatchJob> jobs = new ConcurrentHashMap<>();

    public BatchService(EnrichmentService enrichmentService, PathGuard pathGuard,
                        EnricherProperties props, ObjectMapper mapper, BatchJobStore store) {
        this.enrichmentService = enrichmentService;
        this.pathGuard = pathGuard;
        this.props = props;
        this.mapper = mapper;
        this.store = store;
    }

    @PostConstruct
    void restaurarEstado() {
        List<BatchJob> previos = store.loadAll();
        for (BatchJob j : previos) jobs.put(j.id(), j);
        if (!previos.isEmpty()) {
            log.info("🗂  Restaurados {} lotes del almacenamiento", previos.size());
        }
    }

    /** Crea un trabajo nuevo, devuelve el job listo para arrancar. */
    public BatchJob createJob(String folderPath) {
        boolean hayActivo = jobs.values().stream()
                .anyMatch(j -> j.status() == BatchJob.Status.QUEUED
                        || j.status() == BatchJob.Status.PROCESSING);
        if (hayActivo) {
            throw new IllegalStateException("Ya hay un lote en ejecución. Espera a que termine o cancélalo.");
        }

        Path folder = pathGuard.validateFolder(folderPath);
        List<Path> audios = pathGuard.findAudioFiles(folder);

        if (audios.isEmpty()) {
            throw new IllegalArgumentException("No se encontraron archivos de audio en: " + folder);
        }

        String jobId = UUID.randomUUID().toString().substring(0, 8);
        Path outputDir = Path.of(props.folders().batchOutput()).toAbsolutePath().normalize();
        try { Files.createDirectories(outputDir); } catch (IOException ignored) {}
        Path outputFile = outputDir.resolve("batch_" + jobId + ".jsonl");

        BatchJob job = new BatchJob(jobId, folder, audios.size(), outputFile);
        jobs.put(jobId, job);
        store.save(job);

        return job;
    }

    /** Procesa el lote en segundo plano (hilo único de batchExecutor). */
    @Async("batchExecutor")
    public void processAsync(BatchJob job) {
        job.setStatus(BatchJob.Status.PROCESSING);
        store.save(job);
        List<Path> audios = pathGuard.findAudioFiles(job.folder());

        log.info("🚀 Lote {} arrancado: {} archivos en {}", job.id(), audios.size(), job.folder());

        try (BufferedWriter writer = Files.newBufferedWriter(job.outputFile())) {
            for (Path audio : audios) {
                if (job.cancelRequested()) {
                    log.info("🛑 Lote {} cancelado tras {} archivos", job.id(), job.processed());
                    job.finish(BatchJob.Status.CANCELLED);
                    store.save(job);
                    return;
                }

                try {
                    EnrichmentResult result = enrichmentService.enrich(audio);
                    EnrichmentResult stripped = EnrichmentService.stripCoverData(result);

                    Map<String, Object> line = Map.of(
                            "fileName", audio.getFileName().toString(),
                            "status", "OK",
                            "metadata", stripped.metadata(),
                            "suggestions", stripped.suggestions());

                    writer.write(mapper.writeValueAsString(line));
                    writer.newLine();
                    writer.flush();

                    job.incrementProcessed();
                    if (job.processed() % 50 == 0) {
                        log.info("  📊 Lote {}: {}/{} procesados, {} fallos",
                                job.id(), job.processed(), job.totalFiles(), job.failed());
                    }

                } catch (Exception e) {
                    job.incrementFailed();
                    job.setLastError(audio.getFileName() + ": " + e.getMessage());
                    log.warn("  ⚠ Fallo en '{}': {}", audio.getFileName(), e.getMessage());

                    try {
                        Map<String, Object> errorLine = Map.of(
                                "fileName", audio.getFileName().toString(),
                                "status", "FAILED",
                                "error", e.getMessage() != null ? e.getMessage() : "Error desconocido");

                        writer.write(mapper.writeValueAsString(errorLine));
                        writer.newLine();
                        writer.flush();
                    } catch (IOException ignored) {}

                    job.incrementProcessed();
                }

                if (job.processed() % PERSIST_EVERY_N == 0) store.save(job);
            }

            job.finish(BatchJob.Status.COMPLETED);
            store.save(job);
            log.info("✅ Lote {} completado: {}/{} OK, {} fallos. Resultados: {}",
                    job.id(), job.processed() - job.failed(), job.totalFiles(),
                    job.failed(), job.outputFile());

        } catch (IOException e) {
            job.setLastError("Error de escritura: " + e.getMessage());
            job.finish(BatchJob.Status.FAILED);
            store.save(job);
            log.error("❌ Lote {} falló: {}", job.id(), e.getMessage());
        }
    }

    public Optional<BatchJob> getJob(String jobId) {
        return Optional.ofNullable(jobs.get(jobId));
    }

    public boolean cancelJob(String jobId) {
        BatchJob job = jobs.get(jobId);
        if (job == null) return false;
        if (job.status() == BatchJob.Status.PROCESSING || job.status() == BatchJob.Status.QUEUED) {
            job.requestCancel();
            return true;
        }
        return false;
    }
}
