package com.musarum.mobile.data.network.dto

data class ArtistRefDto(
    val id: Long,
    val name: String
)

data class SongSummaryDto(
    val id: Long,
    val title: String,
    val durationMs: Int?,
    val trackNumber: Int?,
    val artists: List<ArtistRefDto> = emptyList(),
    val coverUrl: String? = null
) {
    val artistsLabel: String
        get() = if (artists.isEmpty()) "Desconocido" else artists.joinToString(", ") { it.name }
}

data class SongDetailsDto(
    val id: Long,
    val title: String,
    val durationMs: Int?,
    val trackNumber: Int?,
    val mbId: String?,
    val isrc: String?,
    val hasLyrics: Boolean,
    val artists: List<ArtistRefDto> = emptyList(),
    val albumTitle: String?,
    val albumId: Long?,
    val coverUrl: String?,
    val genreName: String?,
    val genreId: Long?,
    val isFavorite: Boolean,
    val audioPath: String? = null,
    val bitrate: Int? = null,
    val sampleRate: Int? = null,
    val channels: Int? = null,
    val format: String? = null,
    val fileSize: Long? = null,
    val bpm: Int? = null,
    val musicalKey: String? = null,
    val replayGain: Double? = null,
    val year: Int? = null,
    val discNumber: Int? = null,
    val composer: String? = null,
    val albumYear: Int? = null,
    val hasVideo: Boolean = false
)
