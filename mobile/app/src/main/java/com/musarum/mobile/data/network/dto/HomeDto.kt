package com.musarum.mobile.data.network.dto

/**
 * Convivencia: qu&eacute; est&aacute;n escuchando ahora mismo los otros usuarios. El
 * backend filtra ya a los que tienen {@code shareNowPlaying=false} y se
 * excluye a s&iacute; mismo, no hace falta volver a limpiar aqu&iacute;.
 */
data class NowPlayingUserDto(
    val userId: Long,
    val nick: String,
    val avatarUrl: String?,
    val playedSecondsAgo: Long,
    val song: SongSummaryDto
)

/** P&aacute;gina de "Nuevos descubrimientos". {@code totalNew} alimenta el badge. */
data class DiscoveryPageDto(
    val windowDays: Int,
    val totalNew: Long,
    val items: List<SongSummaryDto>
)

/**
 * Metadatos de una lista inteligente del Home. Las canciones no vienen
 * aqu&iacute;: se piden a demanda en {@code getSmartPlaylistSongs(slug)}.
 *
 * {@code iconHint} es un token sem&aacute;ntico ("party", "focus", "undiscovered",
 * "forgotten") que el m&oacute;vil mapea a icono + color — el backend no decide
 * la presentaci&oacute;n.
 */
data class SmartPlaylistInfoDto(
    val slug: String,
    val title: String,
    val subtitle: String,
    val iconHint: String
)

/**
 * Mini-perfil semanal de otro usuario. Cualquiera de los tres top-* puede
 * ser null si no hay dato suficiente en la ventana.
 */
data class TasteProfileDto(
    val userId: Long,
    val nick: String,
    val avatarUrl: String?,
    val playsThisWeek: Long,
    val topArtist: TopArtistDto?,
    val topGenre: TopGenreDto?,
    val topSong: TopSongDto?
) {
    data class TopArtistDto(
        val id: Long,
        val name: String,
        val imageUrl: String?,
        val plays: Long
    )

    data class TopGenreDto(
        val id: Long,
        val name: String,
        val plays: Long
    )

    data class TopSongDto(
        val id: Long,
        val title: String,
        val coverUrl: String?,
        val artistsLabel: String,
        val plays: Long
    )
}
