package com.musarum.mobile.music.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.ArtistRepository
import com.musarum.mobile.data.network.dto.ArtistSummaryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ArtistsUiState {
    data object Loading : ArtistsUiState()
    data class Ready(
        val artists: List<ArtistSummaryDto>,
        val totalCount: Int,
        val endReached: Boolean,
        val loadingMore: Boolean
    ) : ArtistsUiState()
    data class Error(val message: String) : ArtistsUiState()
}

class ArtistsViewModel(private val repository: ArtistRepository) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 40
        fun factory(repository: ArtistRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ArtistsViewModel(repository) as T
            }
    }

    private val _state = MutableStateFlow<ArtistsUiState>(ArtistsUiState.Loading)
    val state: StateFlow<ArtistsUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private var nextPage = 0

    init { refresh() }

    fun refresh() {
        _refreshing.value = true
        if (_state.value !is ArtistsUiState.Ready) _state.value = ArtistsUiState.Loading
        nextPage = 0
        viewModelScope.launch {
            try {
                val first = repository.page(0, PAGE_SIZE)
                nextPage = 1
                _state.value = ArtistsUiState.Ready(
                    artists = first.content,
                    totalCount = first.totalElements.toInt(),
                    endReached = first.content.size >= first.totalElements,
                    loadingMore = false
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (_state.value !is ArtistsUiState.Ready) {
                    _state.value = ArtistsUiState.Error(t.message ?: "No se pudo cargar")
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    fun loadMore() {
        val current = _state.value as? ArtistsUiState.Ready ?: return
        if (current.loadingMore || current.endReached) return
        _state.value = current.copy(loadingMore = true)
        viewModelScope.launch {
            runCatching { repository.page(nextPage, PAGE_SIZE) }
                .onSuccess { pageDto ->
                    val merged = current.artists + pageDto.content
                    nextPage++
                    _state.value = ArtistsUiState.Ready(
                        artists = merged,
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
