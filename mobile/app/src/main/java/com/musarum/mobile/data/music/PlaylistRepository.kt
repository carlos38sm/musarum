package com.musarum.mobile.data.music

import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.AddCollaboratorRequest
import com.musarum.mobile.data.network.dto.AddSongToPlaylistRequest
import com.musarum.mobile.data.network.dto.PlaylistCreateRequest
import com.musarum.mobile.data.network.dto.PlaylistDetailsDto
import com.musarum.mobile.data.network.dto.PlaylistEntryDto
import com.musarum.mobile.data.network.dto.PlaylistSummaryDto
import com.musarum.mobile.data.network.dto.UserResponseDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class PlaylistRepository(private val apiService: ApiService) {

    private val _playlists = MutableStateFlow<List<PlaylistSummaryDto>>(emptyList())
    val playlists: StateFlow<List<PlaylistSummaryDto>> = _playlists.asStateFlow()

    suspend fun refresh(): List<PlaylistSummaryDto> {
        val list = apiService.getPlaylists()
        _playlists.value = list
        return list
    }

    suspend fun details(id: Long): PlaylistDetailsDto = apiService.getPlaylist(id)

    suspend fun entries(id: Long): List<PlaylistEntryDto> =
        apiService.getPlaylist(id).entries.content

    suspend fun create(
        name: String,
        description: String?,
        isCollaborative: Boolean = false
    ): PlaylistDetailsDto {
        // Hoy mapeamos "colaborativa" sobre el flag isPublic porque es lo
        // que acepta el backend. Cuando añadamos la lógica de invitados,
        // se separa en un campo dedicado en PlaylistCreateRequest.
        val created = apiService.createPlaylist(
            PlaylistCreateRequest(
                name = name,
                description = description,
                isPublic = isCollaborative
            )
        )
        refresh()
        return created
    }

    suspend fun delete(id: Long) {
        apiService.deletePlaylist(id)
        _playlists.value = _playlists.value.filterNot { it.id == id }
    }

    suspend fun addSong(playlistId: Long, songId: Long): PlaylistDetailsDto {
        val result = apiService.addSongToPlaylist(playlistId, AddSongToPlaylistRequest(songId))
        refresh()
        return result
    }

    suspend fun removeEntry(playlistId: Long, entryId: Long): PlaylistDetailsDto {
        val result = apiService.removeSongFromPlaylist(playlistId, entryId)
        refresh()
        return result
    }

    suspend fun moveEntry(playlistId: Long, entryId: Long, newPosition: Int): PlaylistDetailsDto =
        apiService.moveEntry(
            playlistId, entryId,
            com.musarum.mobile.data.network.dto.MoveEntryRequest(newPosition)
        )

    suspend fun addCollaborator(playlistId: Long, nick: String): PlaylistDetailsDto =
        apiService.addCollaborator(playlistId, AddCollaboratorRequest(nick = nick.trim()))

    suspend fun removeCollaborator(playlistId: Long, userId: Long): PlaylistDetailsDto =
        apiService.removeCollaborator(playlistId, userId)

    suspend fun searchUsers(query: String, limit: Int = 10): List<UserResponseDto> =
        if (query.isBlank()) emptyList() else apiService.searchUsers(query.trim(), limit)

    fun clear() {
        _playlists.value = emptyList()
    }
}
