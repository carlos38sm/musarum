package com.musarum.mobile.data.network.dto

data class PlaylistSummaryDto(
    val id: Long,
    val name: String,
    val isPublic: Boolean = false,
    val songCount: Int = 0
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
    val collaborators: List<CollaboratorRefDto> = emptyList()
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
