package com.musarum.mobile.music.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.PlayHistoryRepository
import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.GenreDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeUiState(
    val loading: Boolean = true,
    val lastPlayed: SongSummaryDto? = null,
    val topWeek: List<SongSummaryDto> = emptyList(),
    val recent: List<SongSummaryDto> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val error: String? = null
)

class HomeViewModel(
    private val playHistory: PlayHistoryRepository,
    private val api: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                // supervisorScope-like: cada apartado va en su propio try para
                // que un fallo parcial (p. ej. /me/plays vacío) no tumbe el
                // Home entero y no cancele las otras llamadas.
                val lastPlayed = safeCall { playHistory.lastPlayed(1) }.firstOrNull()
                val topWeek = safeCall { playHistory.topPlays("week", 15) }
                val recent = safeCall { playHistory.recentPlays(20) }
                val genres = safeCall { api.getGenres() }
                _state.value = HomeUiState(
                    loading = false,
                    lastPlayed = lastPlayed,
                    topWeek = topWeek,
                    recent = recent,
                    genres = genres
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = HomeUiState(loading = false, error = t.message ?: "Error")
            }
        }
    }

    private suspend fun <T> safeCall(block: suspend () -> List<T>): List<T> =
        try { coroutineScope { block() } } catch (_: Throwable) { emptyList() }

    companion object {
        fun factory(playHistory: PlayHistoryRepository, api: ApiService): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(playHistory, api) as T
            }
    }
}
