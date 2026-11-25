package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Respuesta de POST /admin/server/wipe-library. Devuelve las cifras que había
 * ANTES del borrado para que el móvil pueda confirmar al OWNER qué se fue.
 */
@Serializable
data class WipeLibrarySummaryDto(
    val songs: Long,
    val artists: Long,
    val albums: Long,
    val genres: Long
)
