package musarum.backend.enrichment;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints de administración del enriquecimiento. Pensado para el OWNER:
 * forzar un re-enriquecimiento manual o lanzar el batch completo. No es
 * de uso del móvil normal — el enriquecimiento automático se dispara al
 * importar (ver {@code EmendatiumAutoEnricher}).
 */
@RestController
@RequestMapping("/enrichment")
class EnrichmentController {

    private final EnrichmentService enrichmentService;

    EnrichmentController(EnrichmentService enrichmentService) {
        this.enrichmentService = enrichmentService;
    }

    @PostMapping("/songs/{songId}")
    ResponseEntity<Void> enrichSong(@PathVariable Long songId) {
        enrichmentService.enrichSong(songId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/batch")
    ResponseEntity<String> enrichBatch() {
        enrichmentService.runBatchEnrichment();
        return ResponseEntity.accepted().body("Enriquecimiento por lotes iniciado en segundo plano");
    }
}