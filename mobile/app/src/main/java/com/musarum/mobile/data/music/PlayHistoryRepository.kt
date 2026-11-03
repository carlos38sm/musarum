package com.musarum.mobile.data.music

import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.LogPlayRequest
import com.musarum.mobile.data.network.dto.SongSummaryDto

/**
 * Historial de reproducciones. El móvil llama a {@link #logPlay} cuando una
 * canción supera los 30s de reproducción (umbral tipo Spotify) y consume los
 * endpoints del backend para pintar carruseles de Home.
 */
class PlayHistoryRepository(private val apiService: ApiService) {

    suspend fun logPlay(songId: Long) {
        runCatching { apiService.logPlay(LogPlayRequest(songId)) }
        // No propagamos el fallo: el log es best-effort, no bloquea el player.
    }

    suspend fun topPlays(range: String = "week", limit: Int = 20): List<SongSummaryDto> =
        apiService.getTopPlays(range, limit)

    suspend fun recentPlays(limit: Int = 20): List<SongSummaryDto> =
        apiService.getRecentPlays(limit)

    suspend fun lastPlayed(limit: Int = 1): List<SongSummaryDto> =
        apiService.getLastPlayed(limit)
}
