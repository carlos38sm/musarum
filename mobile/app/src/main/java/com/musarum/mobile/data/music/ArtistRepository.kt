package com.musarum.mobile.data.music

import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.AlbumCardDto
import com.musarum.mobile.data.network.dto.ArtistDetailsDto
import com.musarum.mobile.data.network.dto.ArtistSummaryDto
import com.musarum.mobile.data.network.dto.PageDto
import com.musarum.mobile.data.network.dto.SongSummaryDto

class ArtistRepository(private val apiService: ApiService) {

    suspend fun page(page: Int, size: Int = 40): PageDto<ArtistSummaryDto> =
        apiService.getArtists(page = page, size = size)

    suspend fun details(id: Long): ArtistDetailsDto = apiService.getArtist(id)

    /** Discografía como tarjetas: con nº canciones, minutos y tipo deducido. */
    suspend fun albums(id: Long): List<AlbumCardDto> =
        apiService.getArtistAlbums(id).content

    suspend fun songs(id: Long): List<SongSummaryDto> =
        apiService.getArtistSongs(id).content
}
