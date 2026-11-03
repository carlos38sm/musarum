package com.musarum.mobile.data.network.dto

data class ArtistSummaryDto(
    val id: Long,
    val name: String,
    val imageUrl: String?
)

data class ArtistDetailsDto(
    val id: Long,
    val name: String,
    val sortName: String?,
    val mbId: String?,
    val country: String?,
    val beginYear: Int?,
    val biography: String?,
    val imageUrl: String?,
    val type: String?,
    val songCount: Long = 0,
    val albumCount: Long = 0,
    val totalDurationMs: Long = 0
)
