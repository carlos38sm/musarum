package com.musarum.mobile.data.network.dto

data class FavoriteDto(
    val id: Long,
    val songId: Long,
    val songTitle: String,
    val durationMs: Int?,
    val artists: List<ArtistRefDto> = emptyList(),
    val albumTitle: String?,
    val coverUrl: String?,
    val favoritedAt: String?
) {
    fun toSongSummary(): SongSummaryDto = SongSummaryDto(
        id = songId,
        title = songTitle,
        durationMs = durationMs,
        trackNumber = null,
        artists = artists
    )
}

data class MarkFavoriteRequest(val songId: Long)
