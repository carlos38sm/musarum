package musarum.backend.enrichment;

import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de administración del enriquecimiento. Solo OWNER — son
 * operaciones caras (fingerprinting + llamadas externas). El enriquecimiento
 * automático al importar se dispara en {@link EmendatiumAutoEnricher}.
 */
@RestController
@RequestMapping("/enrichment")
class EnrichmentController {

    private final EnrichmentService enrichmentService;

    EnrichmentController(EnrichmentService enrichmentService) {
        this.enrichmentService = enrichmentService;
    }

    @PostMapping("/songs/{songId}")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<Void> enrichSong(@PathVariable Long songId) {
        enrichmentService.enrichSong(songId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<String> enrichBatch() {
        enrichmentService.runBatchEnrichment();
        return ResponseEntity.accepted().body("Enriquecimiento por lotes iniciado en segundo plano");
    }
}