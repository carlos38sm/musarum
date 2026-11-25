package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Respuesta de GET /songs/{id}/position. index es 0-based en la lista
 * paginada por defecto (title ASC). El mÃ³vil hace page = index / PAGE_SIZE
 * y despuÃ©s scroll al Ã­ndice absoluto.
 */
@Serializable
data class SongPositionDto(val index: Long)
