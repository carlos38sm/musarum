package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/** Respuesta de POST /admin/server/clean-orphan-lyrics. */
@Serializable
data class CleanOrphanLyricsDto(val deleted: Int)
