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
                // Antes había una guarda "if (_state.value is Ready)" que
                // descartaba emisiones tempranas y a veces hacía que la
                // UI no reflejara borrados hasta reabrir la app. Ahora
                // cualquier cambio del flow (create/delete/addSong/
                // removeEntry) actualiza el state. Mantenemos Error solo
                // si todavía no habíamos conseguido un Ready — si ya
                // estamos navegando la pantalla, un flow nuevo (aunque
                // sea lista vacía) la saca del Error como respuesta
                // natural al cambio.
                _state.value = PlaylistsUiState.Ready(list)
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
        viewModelScope.launch {
            // Defensivo: tras borrar, el repo YA emite _playlists.value
            // filtrado, pero si el collect perdió la emisión por cualquier
            // motivo (p. ej. VM aún en init), un refresh explícito re-pide
            // la lista al backend y la pantalla se actualiza seguro.
            runCatching { repository.delete(id) }
            runCatching { repository.refresh() }
        }
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
