package com.musarum.mobile.profile.folders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.library.LibraryFolderRepository
import com.musarum.mobile.data.network.dto.DiscoveredFolderDto
import com.musarum.mobile.data.network.dto.LibraryFolderDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class LibraryFoldersUiState(
    val loading: Boolean = true,
    val folders: List<LibraryFolderDto> = emptyList(),
    val discoverable: List<DiscoveredFolderDto> = emptyList(),
    val error: String? = null
)

class LibraryFoldersViewModel(
    private val repository: LibraryFolderRepository
) : ViewModel() {

    private val _state = MutableStateFlow(LibraryFoldersUiState())
    val state: StateFlow<LibraryFoldersUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            // Secuencial a propósito: si fuesen async+awaitAll y uno fallara,
            // el job padre cancela y el error llega al main → crash (patrón
            // ya cazado en GenreDetail / ArtistDetail / AlbumDetail).
            val folders = runCatching { repository.list() }
            val discoverable = runCatching { repository.discoverable() }
                .getOrDefault(emptyList())
            folders.onSuccess { list ->
                _state.value = LibraryFoldersUiState(
                    loading = false,
                    folders = list,
                    discoverable = discoverable
                )
            }.onFailure { t ->
                _state.value = _state.value.copy(
                    loading = false,
                    error = t.message ?: "No se pudieron cargar las carpetas"
                )
            }
        }
    }

    fun add(path: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.add(path) }
                .onSuccess { onDone(true, "Carpeta añadida"); refresh() }
                .onFailure { onDone(false, it.message ?: "No se pudo añadir") }
        }
    }

    fun toggle(id: Long, active: Boolean, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.toggle(id, active) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        folders = _state.value.folders.map {
                            if (it.id == id) it.copy(active = active) else it
                        }
                    )
                    onDone(true, if (active) "Activada" else "Desactivada")
                }
                .onFailure { onDone(false, it.message ?: "No se pudo cambiar") }
        }
    }

    fun delete(id: Long, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.delete(id) }
                .onSuccess { onDone(true, "Carpeta eliminada"); refresh() }
                .onFailure { onDone(false, it.message ?: "No se pudo eliminar") }
        }
    }

    companion object {
        fun factory(repository: LibraryFolderRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    LibraryFoldersViewModel(repository) as T
            }
    }
}
