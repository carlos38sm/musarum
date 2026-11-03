package musarum.backend.admin;

/**
 * Snapshot del estado del servidor para la pantalla "Análisis del servidor"
 * del Perfil (OWNER-only). Agrupado en secciones para que el mobile pinte
 * cada tarjeta por separado sin lógica de parsing.
 */
public record ServerStatsDto(
        CatalogStats catalog,
        LyricsStats lyrics,
        DiskStats disk,
        EnrichmentStats enrichment,
        ActivityStats activity,
        TechnicalStats technical
) {

    public record CatalogStats(
            long songs,
            long artists,
            long albums,
            long genres,
            long playlists,
            long totalFavorites,
            long users,
            long blockedUsers
    ) {}

    public record LyricsStats(
            long withLyrics,
            long withoutLyrics,
            double coveragePercent,
            long syncedLyrics,
            long plainLyrics
    ) {}

    public record DiskStats(
            long musicBytes,
            long coversBytes,
            long lyricsBytes,
            long videosBytes,
            long avatarsBytes,
            long databaseBytes,
            long diskFreeBytes,
            long diskTotalBytes
    ) {}

    public record EnrichmentStats(
            long missingCover,
            long missingBpm,
            long missingKey,
            long missingReplayGain,
            long missingMbid
    ) {}

    public record ActivityStats(
            long totalPlays,
            long playsLast7Days,
            long playsLast30Days,
            long activeUsersLast30Days,
            Long lastPlayAt,
            Long lastScanAt,
            String lastScanSummary
    ) {}

    public record TechnicalStats(
            long uptimeMs,
            long memoryUsedBytes,
            long memoryTotalBytes,
            long memoryMaxBytes,
            long orphanLyricFiles
    ) {}
}
