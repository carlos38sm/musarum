package com.musarum.mobile.music.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.PlayHistoryRepository
import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.DiscoveryPageDto
import com.musarum.mobile.data.network.dto.GenreDto
import com.musarum.mobile.data.network.dto.NowPlayingUserDto
import com.musarum.mobile.data.network.dto.SmartPlaylistInfoDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.data.network.dto.TasteProfileDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope

data class HomeUiState(
    val loading: Boolean = true,
    val lastPlayed: SongSummaryDto? = null,
    val topWeek: List<SongSummaryDto> = emptyList(),
    val recent: List<SongSummaryDto> = emptyList(),
    val genres: List<GenreDto> = emptyList(),
    val presence: List<NowPlayingUserDto> = emptyList(),
    val discoveries: DiscoveryPageDto? = null,
    val tasteProfiles: List<TasteProfileDto> = emptyList(),
    val smartPlaylists: List<SmartPlaylistInfoDto> = emptyList(),
    val smartLoadingSlug: String? = null,
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
                // supervisorScope: cada apartado va en su propio async y si
                // uno falla el resto no se cancela (bug conocido documentado
                // en CLAUDE.md §4). Paralelo porque son 7 llamadas
                // independientes al backend y bloquearlas en serie mataba
                // el tiempo de carga del Home.
                supervisorScope {
                    val lastDef = async { safeList { playHistory.lastPlayed(1) }.firstOrNull() }
                    val topDef = async { safeList { playHistory.topPlays("week", 15) } }
                    val recentDef = async { safeList { playHistory.recentPlays(20) } }
                    val genresDef = async { safeList { api.getGenres() } }
                    val presenceDef = async { safeList { api.getHomePresence() } }
                    val discoveriesDef = async { safeNullable { api.getHomeDiscoveries() } }
                    val tasteDef = async { safeList { api.getHomeTasteProfiles() } }
                    val smartDef = async { safeList { api.getSmartPlaylists() } }

                    _state.value = HomeUiState(
                        loading = false,
                        lastPlayed = lastDef.await(),
                        topWeek = topDef.await(),
                        recent = recentDef.await(),
                        genres = genresDef.await(),
                        presence = presenceDef.await(),
                        discoveries = discoveriesDef.await(),
                        tasteProfiles = tasteDef.await(),
                        smartPlaylists = smartDef.await()
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = HomeUiState(loading = false, error = t.message ?: "Error")
            }
        }
    }

    /**
     * Pide las canciones de una lista inteligente y las reproduce. Si el
     * backend devuelve lista vacía (p. ej. "Olvidadas" sin candidatos),
     * invoca onEmpty en vez de reproducir — así la UI puede mostrar snackbar
     * sin encallar el player con cola vacía. El slug "loading" bloquea
     * taps duplicados mientras llega la respuesta.
     */
    fun loadSmartPlaylist(
        slug: String,
        onReady: (List<SongSummaryDto>) -> Unit,
        onEmpty: () -> Unit = {}
    ) {
        if (_state.value.smartLoadingSlug != null) return
        _state.value = _state.value.copy(smartLoadingSlug = slug)
        viewModelScope.launch {
            try {
                val songs = api.getSmartPlaylistSongs(slug, size = 50)
                if (songs.isEmpty()) onEmpty() else onReady(songs)
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                onEmpty()
            } finally {
                _state.value = _state.value.copy(smartLoadingSlug = null)
            }
        }
    }

    private suspend fun <T> safeList(block: suspend () -> List<T>): List<T> =
        try { block() } catch (ce: CancellationException) { throw ce }
        catch (_: Throwable) { emptyList() }

    private suspend fun <T> safeNullable(block: suspend () -> T): T? =
        try { block() } catch (ce: CancellationException) { throw ce }
        catch (_: Throwable) { null }

    companion object {
        fun factory(playHistory: PlayHistoryRepository, api: ApiService): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    HomeViewModel(playHistory, api) as T
            }
    }
}
