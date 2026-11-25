package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Evento del feed "Comunidad" del Home. {@code type} es un string
 * discriminante que el mÃ³vil mapea a icono + verbo:
 *   - "now_playing":  "<nick> estÃ¡ escuchando esta canciÃ³n"
 *   - "favorited":    "<nick> marcÃ³ esta canciÃ³n como favorita"
 *
 * {@code user} puede llegar null si el backend aÃ±ade en el futuro
 * tipos sin autor (p. ej. "canciÃ³n nueva en el catÃ¡logo"). {@code song}
 * siempre viene con lo mÃ­nimo para pintar fila + reproducir.
 */
@Serializable
data class ActivityEventDto(
    val type: String,
    val timestampMs: Long,
    val user: UserRef?,
    val song: SongSummaryDto
) {
    @Serializable
    data class UserRef(
        val id: Long,
        val nick: String,
        val avatarUrl: String?
    )
}
