package com.musarum.mobile.data.music

import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.AlbumCardDto
import com.musarum.mobile.data.network.dto.AlbumDetailsDto
import com.musarum.mobile.data.network.dto.PageDto
import com.musarum.mobile.data.network.dto.SongSummaryDto

class AlbumRepository(private val apiService: ApiService) {

    suspend fun page(page: Int, size: Int = 40): PageDto<AlbumCardDto> =
        apiService.getAlbums(page = page, size = size)

    suspend fun details(id: Long): AlbumDetailsDto = apiService.getAlbum(id)

    suspend fun songs(id: Long): List<SongSummaryDto> =
        apiService.getAlbumSongs(id).content
}
