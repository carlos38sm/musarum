package com.musarum.mobile.data.network.dto

/**
 * Respuesta de POST /admin/server/clean-orphan-entities. Cuenta los
 * artistas / álbumes / géneros borrados (sin canciones asociadas).
 */
data class CleanOrphansSummaryDto(
    val artists: Int,
    val albums: Int,
    val genres: Int
)
