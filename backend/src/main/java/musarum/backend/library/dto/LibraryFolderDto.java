package musarum.backend.library.dto;

/**
 * Carpeta de biblioteca tal como la ve la app.
 * {@code existsOnDisk=false} cuando se añadió una carpeta pero el directorio
 * físico ya no existe (p. ej. tras un rename en el host) — útil para que el
 * móvil muestre un aviso al OWNER.
 */
public record LibraryFolderDto(
        Long id,
        String path,
        String name,
        boolean active,
        boolean existsOnDisk,
        int songCount
) {}
