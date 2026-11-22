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

    /**
     * Artistas con colaboraciones (aparecen en canciones compartidas)
     * ordenados por frecuencia. Si el backend es viejo y no expone el
     * endpoint, el ViewModel cae a lista vacía — la sección no se pinta.
     */
    suspend fun related(id: Long, limit: Int = 20): List<ArtistSummaryDto> =
        apiService.getRelatedArtists(id, limit)
}
