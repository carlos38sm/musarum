package musarum.backend.library.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Alta manual de carpeta. {@code path} debe estar bajo la carpeta paraguas;
 * si no, el service devuelve 400. El OWNER también puede llamar a este
 * endpoint pasando directamente una ruta "discovered" devuelta por
 * {@code GET /admin/folders/available}.
 */
public record AddFolderRequest(
        @NotBlank
        String path
) {}
