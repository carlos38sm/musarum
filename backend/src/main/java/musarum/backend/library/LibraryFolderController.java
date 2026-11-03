package musarum.backend.library;

import jakarta.validation.Valid;
import java.util.List;
import musarum.backend.library.dto.AddFolderRequest;
import musarum.backend.library.dto.DiscoveredFolderDto;
import musarum.backend.library.dto.LibraryFolderDto;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Gestión de carpetas de biblioteca — toda operación requiere OWNER. El
 * móvil permite al OWNER ver qué carpetas se están escaneando, añadir/
 * desactivar sub-carpetas y descubrir nuevas sub-carpetas físicas bajo la
 * carpeta paraguas.
 */
@RestController
@RequestMapping("/admin/folders")
@PreAuthorize("hasRole('OWNER')")
class LibraryFolderController {

    private final LibraryFolderService service;

    LibraryFolderController(LibraryFolderService service) {
        this.service = service;
    }

    @GetMapping
    ResponseEntity<List<LibraryFolderDto>> list() {
        return ResponseEntity.ok(service.listAll());
    }

    /** Sub-carpetas del paraguas aún no registradas en DB. */
    @GetMapping("/available")
    ResponseEntity<List<DiscoveredFolderDto>> available() {
        return ResponseEntity.ok(service.listDiscoverable());
    }

    @PostMapping
    ResponseEntity<LibraryFolderDto> add(@Valid @RequestBody AddFolderRequest request) {
        return ResponseEntity.ok(service.add(request));
    }

    @PatchMapping("/{id}")
    ResponseEntity<LibraryFolderDto> toggle(@PathVariable Long id,
                                            @RequestParam("active") boolean active) {
        return ResponseEntity.ok(service.setActive(id, active));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }
}
