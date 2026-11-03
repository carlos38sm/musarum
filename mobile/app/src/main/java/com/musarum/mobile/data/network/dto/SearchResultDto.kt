package com.musarum.mobile.data.network.dto

data class SearchResultDto(
    val artists: List<ArtistSummaryDto> = emptyList(),
    val albums: List<AlbumCardDto> = emptyList(),
    val songs: List<SongSummaryDto> = emptyList()
)
