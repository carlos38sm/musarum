package com.musarum.mobile.music.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.AlbumRepository
import com.musarum.mobile.data.network.dto.AlbumDetailsDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class AlbumDetailUiState {
    data object Loading : AlbumDetailUiState()
    data class Ready(val album: AlbumDetailsDto, val songs: List<SongSummaryDto>) : AlbumDetailUiState()
    data class Error(val message: String) : AlbumDetailUiState()
}

class AlbumDetailViewModel(
    private val repository: AlbumRepository,
    private val albumId: Long
) : ViewModel() {

    private val _state = MutableStateFlow<AlbumDetailUiState>(AlbumDetailUiState.Loading)
    val state: StateFlow<AlbumDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = AlbumDetailUiState.Loading
        viewModelScope.launch {
            try {
                val album = repository.details(albumId)
                val songs = runCatching { repository.songs(albumId) }
                    .getOrDefault(emptyList())
                _state.value = AlbumDetailUiState.Ready(album, songs)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = AlbumDetailUiState.Error(t.message ?: "Error")
            }
        }
    }

    companion object {
        fun factory(repository: AlbumRepository, albumId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    AlbumDetailViewModel(repository, albumId) as T
            }
    }
}
