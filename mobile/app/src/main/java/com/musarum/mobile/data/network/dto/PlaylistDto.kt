package com.musarum.mobile.data.network.dto

data class PlaylistSummaryDto(
    val id: Long,
    val name: String,
    val isPublic: Boolean = false,
    val songCount: Int = 0,
    // Hasta 4 URLs de las primeras canciones (mosaico 2x2). Slots vacíos
    // llegan como null — el cliente los pinta con placeholder.
    val coverUrls: List<String?> = emptyList()
)

data class PlaylistEntryDto(
    val entryId: Long,
    val position: Int,
    val songId: Long,
    val songTitle: String,
    val durationMs: Int?,
    val artists: List<ArtistRefDto> = emptyList(),
    val albumTitle: String?,
    val coverUrl: String?,
    val musicalKey: String? = null
) {
    fun toSongSummary(): SongSummaryDto = SongSummaryDto(
        id = songId,
        title = songTitle,
        durationMs = durationMs,
        trackNumber = null,
        artists = artists,
        coverUrl = coverUrl
    )
}

data class PlaylistDetailsDto(
    val id: Long,
    val name: String,
    val description: String?,
    val isPublic: Boolean = false,
    val ownerNick: String?,
    val ownerId: Long?,
    val entries: PageDto<PlaylistEntryDto> = PageDto(),
    val collaborators: List<CollaboratorRefDto> = emptyList(),
    // Header enriquecido (bloque B21): mosaico 2×2, totales y tops.
    val coverUrls: List<String?> = emptyList(),
    val songCount: Int = 0,
    val durationMs: Long? = null,
    val topGenreName: String? = null,
    val topArtistName: String? = null,
    val createdAt: String? = null
)

data class CollaboratorRefDto(val id: Long, val nick: String)

data class AddCollaboratorRequest(val nick: String)

data class PlaylistCreateRequest(
    val name: String,
    val description: String? = null,
    val isPublic: Boolean = false
)

data class PlaylistUpdateRequest(
    val name: String,
    val description: String? = null,
    val isPublic: Boolean = false
)

data class AddSongToPlaylistRequest(val songId: Long)
