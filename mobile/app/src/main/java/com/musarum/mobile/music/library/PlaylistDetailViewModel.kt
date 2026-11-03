package com.musarum.mobile.music.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.PlaylistRepository
import com.musarum.mobile.data.network.dto.PlaylistDetailsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PlaylistDetailUiState {
    data object Loading : PlaylistDetailUiState()
    data class Ready(val playlist: PlaylistDetailsDto) : PlaylistDetailUiState()
    data class Error(val message: String) : PlaylistDetailUiState()
}

class PlaylistDetailViewModel(
    private val repository: PlaylistRepository,
    private val playlistId: Long
) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistDetailUiState>(PlaylistDetailUiState.Loading)
    val state: StateFlow<PlaylistDetailUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = PlaylistDetailUiState.Loading
        viewModelScope.launch {
            runCatching { repository.details(playlistId) }
                .onSuccess { _state.value = PlaylistDetailUiState.Ready(it) }
                .onFailure { _state.value = PlaylistDetailUiState.Error(it.message ?: "Error") }
        }
    }

    fun removeEntry(entryId: Long) {
        viewModelScope.launch {
            runCatching { repository.removeEntry(playlistId, entryId) }
                .onSuccess { _state.value = PlaylistDetailUiState.Ready(it) }
        }
    }

    /**
     * Reordena optimistamente: aplicamos la nueva lista en memoria ANTES del
     * POST para que el drag-and-drop se note instantáneo; después confirmamos
     * con la respuesta del servidor. Si falla, re-sincronizamos pidiendo
     * refresh (el estado local vuelve a ser el real).
     */
    fun moveEntry(entryId: Long, fromIndex: Int, toIndex: Int) {
        val current = (_state.value as? PlaylistDetailUiState.Ready)?.playlist ?: return
        if (fromIndex == toIndex) return
        val entries = current.entries.content.toMutableList()
        if (fromIndex !in entries.indices) return
        val moved = entries.removeAt(fromIndex)
        val clampedTo = toIndex.coerceIn(0, entries.size)
        entries.add(clampedTo, moved)
        _state.value = PlaylistDetailUiState.Ready(
            current.copy(entries = current.entries.copy(content = entries))
        )
        viewModelScope.launch {
            runCatching { repository.moveEntry(playlistId, entryId, clampedTo) }
                .onSuccess { _state.value = PlaylistDetailUiState.Ready(it) }
                .onFailure { refresh() }
        }
    }

    fun addCollaborator(nick: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.addCollaborator(playlistId, nick) }
                .onSuccess {
                    _state.value = PlaylistDetailUiState.Ready(it)
                    onDone(true, "$nick añadido como colaborador")
                }
                .onFailure { onDone(false, it.message ?: "No se pudo añadir") }
        }
    }

    fun removeCollaborator(userId: Long, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.removeCollaborator(playlistId, userId) }
                .onSuccess {
                    _state.value = PlaylistDetailUiState.Ready(it)
                    onDone(true, "Colaborador eliminado")
                }
                .onFailure { onDone(false, it.message ?: "No se pudo eliminar") }
        }
    }

    suspend fun searchUsers(query: String) = repository.searchUsers(query)

    companion object {
        fun factory(
            repository: PlaylistRepository,
            playlistId: Long
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlaylistDetailViewModel(repository, playlistId) as T
        }
    }
}
