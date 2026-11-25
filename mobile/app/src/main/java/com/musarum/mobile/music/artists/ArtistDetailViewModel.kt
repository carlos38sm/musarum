package com.musarum.mobile.music.artists

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.ArtistRepository
import com.musarum.mobile.data.network.dto.AlbumCardDto
import com.musarum.mobile.data.network.dto.ArtistDetailsDto
import com.musarum.mobile.data.network.dto.ArtistSummaryDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

sealed class ArtistDetailUiState {
    data object Loading : ArtistDetailUiState()
    data class Ready(
        val artist: ArtistDetailsDto,
        val albums: List<AlbumCardDto> = emptyList(),
        val songs: List<SongSummaryDto> = emptyList(),
        // "Fans también escuchan X" — colaboraciones ordenadas por
        // frecuencia. Vacío = no hay relacionados o el backend no
        // expone el endpoint (caemos silenciosos).
        val related: List<ArtistSummaryDto> = emptyList(),
        // Flags por sección mientras cargan en paralelo tras el header.
        // Permiten a la UI pintar un shimmer / "Cargando…" si quiere, en
        // vez de mostrar sección vacía como "no hay datos".
        val albumsLoading: Boolean = false,
        val songsLoading: Boolean = false,
        val relatedLoading: Boolean = false
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
            try {
                // Paso 1: cargamos SOLO los datos del header (nombre, bio,
                // imagen, counts). Es la llamada más ligera y suele tardar
                // ~100ms. En cuanto llega, la pantalla sale de "cargando".
                val artist = repository.details(artistId)
                _state.value = ArtistDetailUiState.Ready(
                    artist = artist,
                    albumsLoading = true,
                    songsLoading = true,
                    relatedLoading = true
                )

                // Paso 2: álbumes, canciones y relacionados cargan EN PARALELO
                // con supervisorScope — si uno peta, los otros siguen. Cada
                // uno actualiza la sección correspondiente conforme llega,
                // así el usuario va viendo contenido "goteando" en vez de
                // esperar a los 3 round-trips secuenciales anteriores.
                supervisorScope {
                    launch {
                        val list = runCatching { repository.albums(artistId) }
                            .getOrDefault(emptyList())
                        updateReady { it.copy(albums = list, albumsLoading = false) }
                    }
                    launch {
                        val list = runCatching { repository.songs(artistId) }
                            .getOrDefault(emptyList())
                        updateReady { it.copy(songs = list, songsLoading = false) }
                    }
                    launch {
                        val list = runCatching { repository.related(artistId) }
                            .getOrDefault(emptyList())
                        updateReady { it.copy(related = list, relatedLoading = false) }
                    }
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = ArtistDetailUiState.Error(t.message ?: "Error")
            }
        }
    }

    /**
     * Actualización in-place del estado Ready. Si el estado ya no es Ready
     * (p. ej. el user navegó atrás mientras cargaba), se ignora el update.
     */
    private fun updateReady(block: (ArtistDetailUiState.Ready) -> ArtistDetailUiState.Ready) {
        val current = _state.value
        if (current is ArtistDetailUiState.Ready) {
            _state.value = block(current)
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
