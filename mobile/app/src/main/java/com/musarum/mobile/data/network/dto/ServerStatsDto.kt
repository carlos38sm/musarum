package com.musarum.mobile.data.network.dto

/**
 * Snapshot del estado del servidor (pantalla OWNER-only "Análisis del
 * servidor"). Cada sub-record se pinta como su propia tarjeta en el móvil.
 * Nullable en todo lo que el backend puede no saber todavía (nunca hubo
 * reproducciones, nunca hubo scan, etc.).
 */
data class ServerStatsDto(
    val catalog: CatalogStats,
    val lyrics: LyricsStats,
    val disk: DiskStats,
    val enrichment: EnrichmentStats,
    val emendatium: EmendatiumStats?,
    val activity: ActivityStats,
    val technical: TechnicalStats
) {
    data class CatalogStats(
        val songs: Long,
        val artists: Long,
        val albums: Long,
        val genres: Long,
        val playlists: Long,
        val totalFavorites: Long,
        val users: Long,
        val blockedUsers: Long
    )

    data class LyricsStats(
        val withLyrics: Long,
        val withoutLyrics: Long,
        val coveragePercent: Double,
        val syncedLyrics: Long,
        val plainLyrics: Long
    )

    data class DiskStats(
        val musicBytes: Long,
        val coversBytes: Long,
        val lyricsBytes: Long,
        val videosBytes: Long,
        val avatarsBytes: Long,
        val databaseBytes: Long,
        val diskFreeBytes: Long,
        val diskTotalBytes: Long
    )

    data class EnrichmentStats(
        val missingCover: Long,
        val missingBpm: Long,
        val missingKey: Long,
        val missingReplayGain: Long,
        val missingMbid: Long
    )

    /**
     * Estado de Emendatium (servicio aparte de enriquecimiento). Si
     * [enabled] es false, el backend ni siquiera lo tiene configurado. Si
     * [reachable] es false, está configurado pero no se puede hablar con él.
     */
    data class EmendatiumStats(
        val enabled: Boolean,
        val baseUrl: String?,
        val reachable: Boolean,
        val message: String?,
        val lastCheckedAtMs: Long?
    )

    data class ActivityStats(
        val totalPlays: Long,
        val playsLast7Days: Long,
        val playsLast30Days: Long,
        val activeUsersLast30Days: Long,
        val lastPlayAt: Long?,
        val lastScanAt: Long?,
        val lastScanSummary: String?
    )

    data class TechnicalStats(
        val uptimeMs: Long,
        val memoryUsedBytes: Long,
        val memoryTotalBytes: Long,
        val memoryMaxBytes: Long,
        val orphanLyricFiles: Long
    )
}
