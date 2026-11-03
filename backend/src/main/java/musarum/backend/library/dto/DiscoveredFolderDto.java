package musarum.backend.library.dto;

/**
 * Sub-carpeta encontrada bajo la carpeta paraguas que aún no está en DB.
 * Pensado para que el OWNER vea "estas carpetas existen físicamente pero
 * no están añadidas, ¿las añado?".
 */
public record DiscoveredFolderDto(
        String path,
        String name
) {}
