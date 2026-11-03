package emendatium.backend.batch;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Estado de un trabajo de enriquecimiento por lotes. Vive en memoria
 * (ConcurrentHashMap en BatchService). Un solo lote puede correr a la vez.
 */
public class BatchJob {

    public enum Status { QUEUED, PROCESSING, COMPLETED, FAILED, CANCELLED }

    private final String id;
    private final Path folder;
    private final int totalFiles;
    private final Path outputFile;
    private final Instant startedAt;

    private volatile Status status = Status.QUEUED;
    private final AtomicInteger processed = new AtomicInteger(0);
    private final AtomicInteger failed = new AtomicInteger(0);
    private volatile String lastError;
    private volatile Instant finishedAt;
    private volatile boolean cancelRequested = false;

    public BatchJob(String id, Path folder, int totalFiles, Path outputFile) {
        this(id, folder, totalFiles, outputFile, Instant.now());
    }

    /** Constructor para restaurar un lote desde su snapshot en disco. */
    public BatchJob(String id, Path folder, int totalFiles, Path outputFile, Instant startedAt) {
        this.id = id;
        this.folder = folder;
        this.totalFiles = totalFiles;
        this.outputFile = outputFile;
        this.startedAt = startedAt != null ? startedAt : Instant.now();
    }

    // ── Getters ──
    public String id()              { return id; }
    public Path folder()            { return folder; }
    public int totalFiles()         { return totalFiles; }
    public Path outputFile()        { return outputFile; }
    public Instant startedAt()      { return startedAt; }
    public Status status()          { return status; }
    public int processed()          { return processed.get(); }
    public int failed()             { return failed.get(); }
    public String lastError()       { return lastError; }
    public Instant finishedAt()     { return finishedAt; }
    public boolean cancelRequested(){ return cancelRequested; }

    // ── Mutadores (thread-safe) ──
    public void setStatus(Status s)    { this.status = s; }
    public void incrementProcessed()   { this.processed.incrementAndGet(); }
    public void incrementFailed()      { this.failed.incrementAndGet(); }
    public void setLastError(String e) { this.lastError = e; }
    public void finish(Status s)       { this.status = s; this.finishedAt = Instant.now(); }
    public void requestCancel()        { this.cancelRequested = true; }
}