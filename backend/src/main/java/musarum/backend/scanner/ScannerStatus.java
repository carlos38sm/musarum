package musarum.backend.scanner;

/**
 * Snapshot del escaneo en curso. Para que el móvil pueda pintar
 * "Escaneando 1200/7000..." mientras tarda. Cuando running=false, los
 * campos de progreso reflejan el último escaneo que haya corrido en esta
 * sesión del backend, y lastResult trae el resumen final (o null si nunca
 * ha corrido desde el arranque).
 */
public record ScannerStatus(
        boolean running,
        int total,
        int processed,
        String currentFile,
        Long startedAt,
        ImportResult lastResult,
        // "hashing" durante la fase 1 (lectura de ~1 MB + sha-256 por fichero,
        // lenta), "importing" durante la fase 2 (crear filas DB), null cuando
        // no hay escaneo activo. Permite al móvil pintar la etiqueta correcta.
        String phase
) {}
