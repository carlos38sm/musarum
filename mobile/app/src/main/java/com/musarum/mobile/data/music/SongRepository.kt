package com.musarum.mobile.data.music

import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.PageDto
import com.musarum.mobile.data.network.dto.SongDetailsDto
import com.musarum.mobile.data.network.dto.SongSummaryDto

class SongRepository(
    private val apiService: ApiService
) {
    /**
     * Trae una página de canciones ordenadas alfabéticamente por título.
     * Devuelve el PageDto completo (incluye totalElements) para que el
     * ViewModel sepa cuánto espacio reservar en la lista sparse.
     */
    suspend fun page(page: Int, size: Int = 40): PageDto<SongSummaryDto> =
        apiService.getSongs(page = page, size = size)

    suspend fun shuffled(): List<SongSummaryDto> =
        apiService.getShuffledSongs()

    suspend fun letterOffsets(): Map<String, Int> =
        apiService.getLetterOffsets()

    /** Posición absoluta 0-based de la canción en la lista paginada por defecto. */
    suspend fun positionOf(id: Long): Long =
        apiService.getSongPosition(id).index

    suspend fun search(query: String): List<SongSummaryDto> =
        apiService.searchSongs(query = query, page = 0, size = 50).content

    suspend fun details(id: Long): SongDetailsDto =
        apiService.getSong(id)

    suspend fun lyrics(id: Long): String =
        apiService.getLyrics(id)

    /**
     * Fuerza re-búsqueda online. Devuelve null si el backend responde 204
     * (no encontró nada mejor) — el caller mantiene la letra actual en ese
     * caso. Si devuelve texto, es la nueva letra ya cacheada en servidor.
     */
    suspend fun refreshLyrics(id: Long): String? {
        val response = apiService.refreshLyrics(id)
        return if (response.isSuccessful && response.code() != 204) response.body() else null
    }

    /** Borrado completo (solo OWNER). El backend borra DB + archivo. */
    suspend fun delete(id: Long) {
        apiService.deleteSong(id)
    }

    /** Cola de canciones similares a la semilla. */
    suspend fun radio(seedId: Long, size: Int = 50): List<SongSummaryDto> =
        apiService.getRadioFrom(seedId, size)
}
