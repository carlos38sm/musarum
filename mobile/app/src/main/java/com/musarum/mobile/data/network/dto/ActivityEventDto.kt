package com.musarum.mobile.data.network.dto

/**
 * Evento del feed "Comunidad" del Home. {@code type} es un string
 * discriminante que el móvil mapea a icono + verbo:
 *   - "now_playing":  "<nick> está escuchando esta canción"
 *   - "favorited":    "<nick> marcó esta canción como favorita"
 *
 * {@code user} puede llegar null si el backend añade en el futuro
 * tipos sin autor (p. ej. "canción nueva en el catálogo"). {@code song}
 * siempre viene con lo mínimo para pintar fila + reproducir.
 */
data class ActivityEventDto(
    val type: String,
    val timestampMs: Long,
    val user: UserRef?,
    val song: SongSummaryDto
) {
    data class UserRef(
        val id: Long,
        val nick: String,
        val avatarUrl: String?
    )
}
