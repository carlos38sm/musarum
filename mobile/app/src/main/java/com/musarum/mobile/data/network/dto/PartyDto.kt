package com.musarum.mobile.data.network.dto

data class PartyDto(
    val id: Long,
    val hostUserId: Long,
    val hostNick: String?,
    val iAmHost: Boolean,
    val currentSong: SongSummaryDto?,
    val queue: List<PartyQueueItemDto>,
    val members: List<PartyMemberDto>,
    val updatedAt: String?
)

data class PartyQueueItemDto(
    val id: Long,
    val position: Int,
    val addedByUserId: Long,
    val addedByNick: String?,
    val song: SongSummaryDto?
)

data class PartyMemberDto(
    val userId: Long,
    val nick: String?,
    val avatarUrl: String?
)

data class AddPartySongRequest(val songId: Long)
data class SetPartyCurrentRequest(val songId: Long?)
