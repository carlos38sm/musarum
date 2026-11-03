package com.musarum.mobile.music.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.ArtistRepository
import com.musarum.mobile.data.network.dto.AlbumCardDto
import com.musarum.mobile.data.network.dto.ArtistDetailsDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class ArtistDetailUiState {
    data object Loading : ArtistDetailUiState()
    data class Ready(
        val artist: ArtistDetailsDto,
        val albums: List<AlbumCardDto>,
        val songs: List<SongSummaryDto>
    ) : ArtistDetailUiState()
    data class Error(val message: String) : ArtistDetailUiState()
}

class ArtistDetailViewModel(
    private val repository: ArtistRepository,
    private val artistId: Long
) : ViewModel() {

    private val _state = MutableStateFlow<ArtistDetailUiState>(ArtistDetailUiState.Loading)
    val state: StateFlow<ArtistDetailUiState> = _state.asStateFlow()

    init { load() }

    fun load() {
        _state.value = ArtistDetailUiState.Loading
        viewModelScope.launch {
            // Secuencial a propósito. Si fuesen `async` en paralelo y uno
            // fallara, el Job padre se cancela y la excepción llega al main
            // dispatcher → crash. Mismo patrón que arreglamos en GenreDetail.
            try {
                val artist = repository.details(artistId)
                val albums = runCatching { repository.albums(artistId) }
                    .getOrDefault(emptyList())
                val songs = runCatching { repository.songs(artistId) }
                    .getOrDefault(emptyList())
                _state.value = ArtistDetailUiState.Ready(artist, albums, songs)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = ArtistDetailUiState.Error(t.message ?: "Error")
            }
        }
    }

    companion object {
        fun factory(repository: ArtistRepository, artistId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ArtistDetailViewModel(repository, artistId) as T
            }
    }
}
