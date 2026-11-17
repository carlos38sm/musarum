package com.musarum.mobile.data.network.dto

/** Snapshot del estado de reproducción publicado por un dispositivo. */
data class HandoffDto(
    val userId: Long,
    val userNick: String?,
    val userAvatarUrl: String?,
    val song: SongSummaryDto?,
    val positionMs: Long,
    val deviceLabel: String?,
    val updatedAt: String?,
    val mine: Boolean
)

data class PublishHandoffRequest(
    val songId: Long,
    val positionMs: Long,
    val deviceLabel: String?
)
