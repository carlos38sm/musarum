package com.musarum.mobile.data.network.dto

data class AlbumCardDto(
    val id: Long,
    val title: String,
    val coverUrl: String?,
    val artistName: String?,
    val artistId: Long?,
    val releaseYear: Int? = null,
    val songCount: Long = 0,
    val totalDurationMs: Long = 0,
    val inferredType: String? = null
)

data class AlbumSummaryDto(
    val id: Long,
    val title: String,
    val coverUrl: String?
)

data class AlbumDetailsDto(
    val id: Long,
    val title: String,
    val releaseYear: Int?,
    val type: String?,
    val coverUrl: String?,
    val mbId: String?,
    val artistName: String?,
    val artistId: Long?,
    val songCount: Long = 0,
    val totalDurationMs: Long = 0,
    val inferredType: String? = null
)
