package com.musarum.mobile.music.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.AlbumRepository
import com.musarum.mobile.data.network.dto.AlbumCardDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AlbumsUiState {
    data object Loading : AlbumsUiState()
    data class Ready(
        val albums: List<AlbumCardDto>,
        val totalCount: Int,
        val endReached: Boolean,
        val loadingMore: Boolean
    ) : AlbumsUiState()
    data class Error(val message: String) : AlbumsUiState()
}

class AlbumsViewModel(private val repository: AlbumRepository) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 40
        fun factory(repository: AlbumRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AlbumsViewModel(repository) as T
            }
    }

    private val _state = MutableStateFlow<AlbumsUiState>(AlbumsUiState.Loading)
    val state: StateFlow<AlbumsUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var nextPage = 0

    init { refresh() }

    fun refresh() {
        _refreshing.value = true
        if (_state.value !is AlbumsUiState.Ready) _state.value = AlbumsUiState.Loading
        nextPage = 0
        viewModelScope.launch {
            try {
                val first = repository.page(0, PAGE_SIZE)
                nextPage = 1
                _state.value = AlbumsUiState.Ready(
                    albums = first.content,
                    totalCount = first.totalElements.toInt(),
                    endReached = first.content.size >= first.totalElements,
                    loadingMore = false
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (_state.value !is AlbumsUiState.Ready) {
                    _state.value = AlbumsUiState.Error(t.message ?: "No se pudo cargar")
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    /** Pide la siguiente página. Idempotente: ignora si ya está cargando o
     *  si no quedan más. */
    fun loadMore() {
        val current = _state.value as? AlbumsUiState.Ready ?: return
        if (current.loadingMore || current.endReached) return
        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            runCatching { repository.page(nextPage, PAGE_SIZE) }
                .onSuccess { pageDto ->
                    val merged = current.albums + pageDto.content
                    nextPage++
                    _state.value = AlbumsUiState.Ready(
                        albums = merged,
                        totalCount = pageDto.totalElements.toInt(),
                        endReached = merged.size >= pageDto.totalElements,
                        loadingMore = false
                    )
                }
                .onFailure {
                    _state.value = current.copy(loadingMore = false)
                }
        }
    }
}
