package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Respuesta de POST /admin/server/clean-orphan-entities. Cuenta los
 * artistas / Ã¡lbumes / gÃ©neros borrados (sin canciones asociadas).
 */
@Serializable
data class CleanOrphansSummaryDto(
    val artists: Int,
    val albums: Int,
    val genres: Int
)
