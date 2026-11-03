package com.musarum.mobile.profile.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.ServerStatsDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ServerStatsUiState {
    data object Loading : ServerStatsUiState
    data class Ready(val stats: ServerStatsDto) : ServerStatsUiState
    data class Error(val message: String) : ServerStatsUiState
}

/**
 * ViewModel de la pantalla "Análisis del servidor". Carga una sola vez al
 * entrar y permite recargar manualmente desde el botón del TopAppBar.
 */
class ServerStatsViewModel(
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow<ServerStatsUiState>(ServerStatsUiState.Loading)
    val state: StateFlow<ServerStatsUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ServerStatsUiState.Loading
            try {
                val stats = apiService.serverStats()
                _state.value = ServerStatsUiState.Ready(stats)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = ServerStatsUiState.Error(
                    t.message ?: "No se pudo cargar el análisis del servidor"
                )
            }
        }
    }

    /** Borra los .lrc huérfanos del servidor y refresca el panel. */
    fun cleanOrphanLyrics() {
        viewModelScope.launch {
            try {
                apiService.cleanOrphanLyrics()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) { /* best-effort: en el refresh se verá si quedó algo */ }
            refresh()
        }
    }

    companion object {
        fun factory(apiService: ApiService): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ServerStatsViewModel(apiService) as T
            }
    }
}
