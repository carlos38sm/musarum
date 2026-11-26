package emendatium.backend.api;

import emendatium.backend.batch.BatchJob;
import emendatium.backend.batch.BatchService;
import emendatium.backend.enrichment.EnrichmentService;
import emendatium.backend.enrichment.EnrichmentService.EnrichmentResult;
import emendatium.backend.naming.FileOrganizer;
import emendatium.backend.naming.FileRenamer;
import emendatium.backend.naming.FilenameBuilder;
import emendatium.backend.shared.PathGuard;
import emendatium.backend.shared.TrackMetadata;
import emendatium.backend.tagging.TagWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class EnrichController {

    private final EnrichmentService enrichmentService;
    private final BatchService batchService;
    private final PathGuard pathGuard;
    private final TagWriter tagWriter;
    private final FileRenamer fileRenamer;
    private final FileOrganizer fileOrganizer;
    private final FilenameBuilder filenameBuilder;

    // ════════════════════════════════════════════════════════════════════════
    // 1. ENRIQUECIMIENTO INDIVIDUAL (síncrono)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/enrich
     * Body: {"path": "C:/ruta/cancion.mp3"}
     *
     * Enriquece una canción: identifica por huella, consulta APIs, analiza audio.
     * Devuelve metadata completa + sugerencias. No modifica el archivo.
     */
    @PostMapping("/enrich")
    public ResponseEntity<EnrichmentResult> enrich(@RequestBody Map<String, String> body) {
        String rawPath = body.get("path");
        Path audio = pathGuard.validateAudio(rawPath);

        EnrichmentResult result = enrichmentService.enrich(audio);
        EnrichmentResult stripped = EnrichmentService.stripCoverData(result);

        return ResponseEntity.ok(stripped);
    }

    // ════════════════════════════════════════════════════════════════════════
    // 2. APLICAR TAGS AL ARCHIVO
    // ════════════════════════════════════════════════════════════════════════

    public record ApplyRequest(String path, TrackMetadata metadata) {}
    public record ApplyResponse(String path, String message) {}

    /**
     * POST /api/enrich/apply
     * Body: {"path": "...", "metadata": {...}}
     *
     * Escribe los metadatos recibidos sobre el archivo (sobrescribe la copia
     * original tras validar). Formatos soportados: mp3, flac, m4a, ogg.
     */
    @PostMapping("/enrich/apply")
    public ResponseEntity<ApplyResponse> apply(@RequestBody ApplyRequest req) {
        Path audio = pathGuard.validateAudio(req.path());
        tagWriter.write(audio, req.metadata());
        return ResponseEntity.ok(new ApplyResponse(audio.toString(), "Tags aplicados"));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3. RENOMBRAR ARCHIVO
    // ════════════════════════════════════════════════════════════════════════

    public record RenameRequest(String path, TrackMetadata metadata, String overrideName) {}
    public record RenameResponse(String oldPath, String newPath, boolean changed) {}

    /**
     * POST /api/enrich/rename
     * Body: {"path": "...", "metadata": {...}}  — usa la plantilla configurada
     *   o: {"path": "...", "overrideName": "nombre sin extension"}
     *
     * Renombra el archivo en su misma carpeta preservando la extensión.
     * Si el nombre destino ya existe, añade " (2)", " (3)"... para no pisarlo.
     */
    @PostMapping("/enrich/rename")
    public ResponseEntity<RenameResponse> rename(@RequestBody RenameRequest req) {
        Path audio = pathGuard.validateAudio(req.path());

        String newBase = req.overrideName();
        if (newBase == null || newBase.isBlank()) {
            if (req.metadata() == null) {
                throw new IllegalArgumentException("Hace falta 'metadata' o 'overrideName'");
            }
            newBase = filenameBuilder.build(req.metadata());
        }

        Path moved = fileRenamer.renameInPlace(audio, newBase);
        boolean changed = !moved.equals(audio);
        return ResponseEntity.ok(new RenameResponse(
                audio.toString(), moved.toString(), changed));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 3b. ORGANIZAR (mover a raíz de la biblioteca)
    // ════════════════════════════════════════════════════════════════════════

    public record OrganizeRequest(String path, TrackMetadata metadata, String overrideName) {}

    /**
     * POST /api/enrich/organize
     * Body: {"path": "...", "metadata": {...}}  — usa la plantilla configurada
     *   o: {"path": "...", "overrideName": "nombre sin extension"}
     *
     * Mueve el fichero a la RAÍZ de la biblioteca ({@code enricher.folders.input})
     * con el nombre indicado. Diseñado para sacar canciones recién subidas de
     * {@code _inbox/} una vez Emendatium las identifica. Si en destino hay
     * colisión, añade " (2)", " (3)"... El backend llama a este endpoint
     * SOLO cuando el fichero estaba en la bandeja de entrada — para otros
     * paths usa {@code /enrich/rename} que mantiene la carpeta.
     */
    @PostMapping("/enrich/organize")
    public ResponseEntity<RenameResponse> organize(@RequestBody OrganizeRequest req) {
        Path audio = pathGuard.validateAudio(req.path());

        String newBase = req.overrideName();
        if (newBase == null || newBase.isBlank()) {
            if (req.metadata() == null) {
                throw new IllegalArgumentException("Hace falta 'metadata' o 'overrideName'");
            }
            newBase = filenameBuilder.build(req.metadata());
        }

        Path moved = fileOrganizer.moveToRoot(audio, newBase);
        boolean changed = !moved.equals(audio);
        return ResponseEntity.ok(new RenameResponse(
                audio.toString(), moved.toString(), changed));
    }

    public record QuarantineRequest(String path) {}

    /**
     * POST /api/enrich/quarantine
     * Body: {"path": "..."}
     *
     * Mueve el fichero a {@code _inbox/sin-identificar/} preservando su
     * nombre original. El backend llama aquí cuando Emendatium NO
     * identificó la pista — así la raíz de {@code _inbox/} queda limpia
     * (solo contiene ficheros en proceso) y el OWNER puede gestionar
     * las fallidas desde una pantalla dedicada.
     */
    @PostMapping("/enrich/quarantine")
    public ResponseEntity<RenameResponse> quarantine(@RequestBody QuarantineRequest req) {
        Path audio = pathGuard.validateAudio(req.path());
        Path moved = fileOrganizer.moveToQuarantine(audio);
        boolean changed = !moved.equals(audio);
        return ResponseEntity.ok(new RenameResponse(
                audio.toString(), moved.toString(), changed));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 4. ENRIQUECIMIENTO POR LOTES (asíncrono)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * POST /api/enrich/batch
     * Body: {"folderPath": "C:/ruta/biblioteca"}
     *
     * Arranca un trabajo en segundo plano que procesa todos los audios de la
     * carpeta. Devuelve un jobId al instante. Consulta progreso con GET.
     * Solo puede haber un lote activo a la vez (409 si ya hay uno).
     */
    @PostMapping("/enrich/batch")
    public ResponseEntity<BatchStatusDto> startBatch(@RequestBody Map<String, String> body) {
        String folderPath = body.get("folderPath");
        BatchJob job = batchService.createJob(folderPath);
        batchService.processAsync(job);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(toDto(job));
    }

    /**
     * GET /api/enrich/batch/{jobId}
     *
     * Consulta el progreso del lote.
     */
    @GetMapping("/enrich/batch/{jobId}")
    public ResponseEntity<BatchStatusDto> batchStatus(@PathVariable String jobId) {
        return batchService.getJob(jobId)
                .map(job -> ResponseEntity.ok(toDto(job)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * DELETE /api/enrich/batch/{jobId}
     *
     * Solicita cancelar el lote. El procesamiento se detiene en la siguiente
     * canción. Resultados parciales se conservan.
     */
    @DeleteMapping("/enrich/batch/{jobId}")
    public ResponseEntity<Map<String, String>> cancelBatch(@PathVariable String jobId) {
        boolean cancelled = batchService.cancelJob(jobId);
        if (!cancelled) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(Map.of("message", "Cancelación solicitada para lote " + jobId));
    }

    // ════════════════════════════════════════════════════════════════════════
    // 5. PORTADAS (proxy ligero)
    // ════════════════════════════════════════════════════════════════════════

    /**
     * GET /api/covers/{mbReleaseId}
     *
     * Redirige al CoverArtArchive para la portada del release.
     */
    @GetMapping("/covers/{mbReleaseId}")
    public ResponseEntity<Void> getCover(@PathVariable String mbReleaseId) {
        String url = "https://coverartarchive.org/release/" + mbReleaseId + "/front-500";
        return ResponseEntity.status(HttpStatus.FOUND)
                .header("Location", url)
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════
    // DTOs
    // ════════════════════════════════════════════════════════════════════════

    public record BatchStatusDto(
            String jobId,
            String status,
            int totalFiles,
            int processed,
            int failed,
            long elapsedSeconds,
            Long estimatedRemainingSeconds,
            String lastError,
            String outputFile
    ) {}

    private BatchStatusDto toDto(BatchJob job) {
        long elapsed = Duration.between(job.startedAt(), Instant.now()).toSeconds();

        Long remaining = null;
        if (job.processed() > 0 && job.status() == BatchJob.Status.PROCESSING) {
            double secsPerFile = (double) elapsed / job.processed();
            int quedan = job.totalFiles() - job.processed();
            remaining = Math.round(secsPerFile * quedan);
        }

        return new BatchStatusDto(
                job.id(),
                job.status().name(),
                job.totalFiles(),
                job.processed(),
                job.failed(),
                elapsed,
                remaining,
                job.lastError(),
                job.outputFile() != null ? job.outputFile().toString() : null);
    }
}
