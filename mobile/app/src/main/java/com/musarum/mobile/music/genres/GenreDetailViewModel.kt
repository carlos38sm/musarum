package com.musarum.mobile.music.genres

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.GenreDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface GenreDetailUiState {
    data object Loading : GenreDetailUiState
    data class Error(val message: String) : GenreDetailUiState
    data class Ready(
        val genre: GenreDto,
        val songs: List<SongSummaryDto>
    ) : GenreDetailUiState
}

class GenreDetailViewModel(
    private val api: ApiService,
    private val genreId: Long
) : ViewModel() {

    private val _state = MutableStateFlow<GenreDetailUiState>(GenreDetailUiState.Loading)
    val state: StateFlow<GenreDetailUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = GenreDetailUiState.Loading
        viewModelScope.launch {
            // Secuencial a propósito: si fuese dos `async` en paralelo y uno
            // falla, el Job padre se cancela y la excepción llega al handler
            // de main → crash. Con `try/catch` directo dentro del `launch`
            // la excepción está domada en su sitio y no propaga.
            try {
                val genres = api.getGenres()
                val genre = genres.firstOrNull { it.id == genreId }
                    ?: error("Género no encontrado")
                val songsPage = api.getGenreSongs(genreId)
                _state.value = GenreDetailUiState.Ready(
                    genre = genre,
                    songs = songsPage.content
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = GenreDetailUiState.Error(
                    t.message?.takeIf { it.isNotBlank() } ?: "No se pudo cargar el género"
                )
            }
        }
    }

    companion object {
        fun factory(api: ApiService, genreId: Long): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    GenreDetailViewModel(api, genreId) as T
            }
    }
}
