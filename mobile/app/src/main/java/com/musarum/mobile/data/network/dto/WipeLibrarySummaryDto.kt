package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Respuesta de POST /admin/server/wipe-library. Devuelve las cifras que habÃ­a
 * ANTES del borrado para que el mÃ³vil pueda confirmar al OWNER quÃ© se fue.
 */
@Serializable
data class WipeLibrarySummaryDto(
    val songs: Long,
    val artists: Long,
    val albums: Long,
    val genres: Long
)
