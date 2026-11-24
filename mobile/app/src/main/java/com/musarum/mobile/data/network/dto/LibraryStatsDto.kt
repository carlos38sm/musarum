package com.musarum.mobile.data.network.dto

/**
 * KPIs globales de la biblioteca servidos por GET /songs/library-stats.
 * Consumido por el hero de SongsScreen para pintar "X pistas · Y horas".
 */
data class LibraryStatsDto(
    val totalSongs: Long,
    val totalDurationMs: Long
)
