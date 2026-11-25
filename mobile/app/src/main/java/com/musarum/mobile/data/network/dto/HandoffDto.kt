package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/** Snapshot del estado de reproducciÃ³n publicado por un dispositivo. */
@Serializable
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

@Serializable

data class PublishHandoffRequest(
    val songId: Long,
    val positionMs: Long,
    val deviceLabel: String?
)
