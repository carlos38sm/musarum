package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/** CanciÃ³n que otro user me enviÃ³ â€” item de la bandeja. */
@Serializable
data class InboxItemDto(
    val id: Long,
    val fromUserId: Long,
    val fromNick: String?,
    val fromAvatarUrl: String?,
    val song: SongSummaryDto?,
    val note: String?,
    val sentAt: String?,  // ISO-8601, el mÃ³vil no lo parsea hoy
    val read: Boolean
)

/** POST /me/inbox â€” "le envÃ­o X canciÃ³n a Y user". */
@Serializable
data class SendShareRequest(
    val toUserId: Long,
    val songId: Long,
    val note: String? = null
)
