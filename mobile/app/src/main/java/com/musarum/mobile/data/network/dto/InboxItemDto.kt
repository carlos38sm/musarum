package com.musarum.mobile.data.network.dto

/** Canción que otro user me envió — item de la bandeja. */
data class InboxItemDto(
    val id: Long,
    val fromUserId: Long,
    val fromNick: String?,
    val fromAvatarUrl: String?,
    val song: SongSummaryDto?,
    val note: String?,
    val sentAt: String?,  // ISO-8601, el móvil no lo parsea hoy
    val read: Boolean
)

/** POST /me/inbox — "le envío X canción a Y user". */
data class SendShareRequest(
    val toUserId: Long,
    val songId: Long,
    val note: String? = null
)
