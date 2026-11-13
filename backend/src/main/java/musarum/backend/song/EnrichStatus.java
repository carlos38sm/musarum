package musarum.backend.song;

/**
 * Estado del enriquecimiento Emendatium para una canción.
 *
 * <ul>
 *   <li>{@code null} en DB → nunca intentada. El scanner la encolará la
 *       próxima vez.</li>
 *   <li>{@link #OK} → Emendatium devolvió metadata y se aplicó (bpm, bio,
 *       etc. están en DB). No se re-intenta salvo que el OWNER fuerce.</li>
 *   <li>{@link #FAILED_NO_MATCH} → Emendatium corrió pero no identificó la
 *       pista (fingerprint no encontrado, sin MBID, sin resultados). No
 *       sirve reintentar automáticamente — resultará igual. El OWNER puede
 *       forzar si cambian las fuentes.</li>
 *   <li>{@link #FAILED_TRANSIENT} → fallo temporal (timeout, red caída,
 *       5xx del propio Emendatium). Reintentable — "Re-enriquecer todo"
 *       sí lo cubre.</li>
 * </ul>
 */
public enum EnrichStatus {
    OK,
    FAILED_NO_MATCH,
    FAILED_TRANSIENT
}
