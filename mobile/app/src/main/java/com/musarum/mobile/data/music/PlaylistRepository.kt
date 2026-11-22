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

    // Playlists en las que soy colaborador (me invitó otro user). Se
    // mantienen en un flow aparte de las propias para que Library pueda
    // pintarlas en una sección distinta y refrescarlas sin pisar las
    // otras. Vacío = no soy colaborador en ninguna (lo normal al principio).
    private val _shared = MutableStateFlow<List<PlaylistSummaryDto>>(emptyList())
    val shared: StateFlow<List<PlaylistSummaryDto>> = _shared.asStateFlow()

    suspend fun refresh(): List<PlaylistSummaryDto> {
        val list = apiService.getPlaylists()
        _playlists.value = list
        // Refrescamos también las compartidas aquí (en el mismo evento
        // de "la biblioteca pidió refresh") para que ambas secciones
        // se actualicen juntas. Si el endpoint no responde — backend
        // antiguo — dejamos las compartidas como estaban en memoria.
        runCatching { _shared.value = apiService.getSharedPlaylists() }
        return list
    }

    suspend fun details(id: Long): PlaylistDetailsDto = apiService.getPlaylist(id)

    suspend fun update(id: Long, name: String, description: String?, isPublic: Boolean): PlaylistDetailsDto =
        apiService.updatePlaylist(
            id,
            com.musarum.mobile.data.network.dto.PlaylistUpdateRequest(
                name = name, description = description, isPublic = isPublic
            )
        )

    suspend fun entries(id: Long): List<PlaylistEntryDto> =
        apiService.getPlaylist(id).entries.content

    suspend fun create(
        name: String,
        description: String?
    ): PlaylistDetailsDto {
        // La playlist se crea siempre privada (isPublic=false). Si el user
        // quiere que colaboren otros, lo hace desde los detalles invitando
        // por nick — los colaboradores pueden ver/editar aunque no sea
        // pública (canView cubre owner + collaborator). isPublic queda
        // reservado para un posible modo "visible por cualquier user" sin
        // necesidad de invitación, que hoy no usa la UI.
        val created = apiService.createPlaylist(
            PlaylistCreateRequest(
                name = name,
                description = description,
                isPublic = false
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

    /**
     * Añade N canciones en una sola petición. Mucho más eficiente que
     * N calls a addSong — el backend ignora las ya existentes.
     */
    suspend fun addSongsBulk(playlistId: Long, songIds: List<Long>): PlaylistDetailsDto {
        val result = apiService.addSongsToPlaylistBulk(
            playlistId,
            com.musarum.mobile.data.network.dto.AddSongsBulkRequest(songIds)
        )
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
        _shared.value = emptyList()
    }
}
