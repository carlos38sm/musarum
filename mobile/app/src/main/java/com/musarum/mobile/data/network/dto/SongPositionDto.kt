package com.musarum.mobile.data.network.dto

/**
 * Respuesta de GET /songs/{id}/position. index es 0-based en la lista
 * paginada por defecto (title ASC). El móvil hace page = index / PAGE_SIZE
 * y después scroll al índice absoluto.
 */
data class SongPositionDto(val index: Long)
