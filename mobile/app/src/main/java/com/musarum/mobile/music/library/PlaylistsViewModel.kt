package com.musarum.mobile.music.library

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.PlaylistRepository
import com.musarum.mobile.data.network.dto.PlaylistSummaryDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class PlaylistsUiState {
    data object Loading : PlaylistsUiState()
    data class Ready(val playlists: List<PlaylistSummaryDto>) : PlaylistsUiState()
    data class Error(val message: String) : PlaylistsUiState()
}

class PlaylistsViewModel(private val repository: PlaylistRepository) : ViewModel() {

    private val _state = MutableStateFlow<PlaylistsUiState>(PlaylistsUiState.Loading)
    val state: StateFlow<PlaylistsUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        viewModelScope.launch {
            repository.playlists.collect { list ->
                // Si ya pasamos a Ready (por el primer refresh), nos quedamos
                // suscritos para reflejar cambios posteriores (crear/borrar).
                if (_state.value is PlaylistsUiState.Ready) {
                    _state.value = PlaylistsUiState.Ready(list)
                }
            }
        }
        refresh()
    }

    fun refresh() {
        _refreshing.value = true
        viewModelScope.launch {
            if (_state.value !is PlaylistsUiState.Ready) {
                _state.value = PlaylistsUiState.Loading
            }
            runCatching { repository.refresh() }
                .onSuccess {
                    // Al terminar el refresh, pasamos a Ready SIEMPRE —
                    // incluso con lista vacía (antes esto se quedaba atascado
                    // en Loading cuando el usuario no tenía playlists).
                    _state.value = PlaylistsUiState.Ready(repository.playlists.value)
                }
                .onFailure { e ->
                    if (_state.value !is PlaylistsUiState.Ready) {
                        _state.value = PlaylistsUiState.Error(e.message ?: "Error")
                    }
                }
            _refreshing.value = false
        }
    }

    fun create(
        name: String,
        description: String?,
        isCollaborative: Boolean = false,
        onDone: () -> Unit = {}
    ) {
        viewModelScope.launch {
            runCatching {
                repository.create(
                    name = name.trim(),
                    description = description?.trim()?.ifBlank { null },
                    isCollaborative = isCollaborative
                )
            }.onSuccess { onDone() }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch { runCatching { repository.delete(id) } }
    }

    companion object {
        fun factory(repository: PlaylistRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PlaylistsViewModel(repository) as T
            }
    }
}
