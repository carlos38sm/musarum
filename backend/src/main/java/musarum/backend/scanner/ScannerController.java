package musarum.backend.scanner;

import java.util.concurrent.CompletableFuture;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/scanner")
class ScannerController {

    private final LibraryScanner libraryScanner;

    ScannerController(LibraryScanner libraryScanner) {
        this.libraryScanner = libraryScanner;
    }

    /**
     * Lanzar escaneo es operación pesada (hash SHA-256 de miles de ficheros
     * + INSERTs). Solo OWNER. Antes era abierta → cualquier MEMBER podía
     * saturar el servidor pulsando "Rescanear" en bucle.
     */
    @PostMapping("/import")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<String> importSongs() {
        CompletableFuture<ImportResult> future = libraryScanner.importNewSongs();

        future.thenAccept(result ->
                org.slf4j.LoggerFactory.getLogger(ScannerController.class)
                        .info("Import finalizado — disco: {}, importadas: {}, fallidas: {}, "
                                        + "borradas: {}, duplicadas: {}, reubicadas: {}, hash errors: {}",
                                result.totalOnDisk(), result.imported(), result.failed(),
                                result.deleted(), result.duplicates(), result.relocated(),
                                result.hashErrors())
        );

        return ResponseEntity.accepted().body("Escaneo iniciado en segundo plano");
    }

    /**
     * Snapshot del progreso del escaneo. Pensado para que el móvil haga polling
     * cada 1-2 seg mientras running=true y muestre un contador en vivo. Al
     * terminar, lastResult trae el desglose final.
     */
    @GetMapping("/status")
    ResponseEntity<ScannerStatus> status() {
        return ResponseEntity.ok(libraryScanner.currentStatus());
    }
}