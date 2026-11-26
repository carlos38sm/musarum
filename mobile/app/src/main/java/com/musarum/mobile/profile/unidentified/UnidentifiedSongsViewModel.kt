package com.musarum.mobile.profile.unidentified

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.SongRepository
import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.SongDetailsDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.data.network.dto.TagEditRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class UnidentifiedUiState {
    data object Loading : UnidentifiedUiState()
    data class Ready(val songs: List<SongSummaryDto>) : UnidentifiedUiState()
    data class Error(val message: String) : UnidentifiedUiState()
}

/**
 * ViewModel de la pantalla "Canciones sin identificar".
 *
 * Carga la lista del backend al arrancar y permite:
 *  - Pedir los detalles de UNA canción para abrir el EditTagsDialog.
 *  - Aplicar un edit de tags → recarga la lista (si Emendatium ya la
 *    identifica, la canción desaparece porque se mueve de carpeta).
 *  - Reintentar enriquecimiento sin editar.
 *
 * Usa el endpoint existente {@code POST /songs/{id}/re-enrich} (ya
 * OWNER-only) tanto para reintentar directamente como para reintentar
 * tras editar tags. No añadimos otro nuevo para no tener dos caminos.
 */
class UnidentifiedSongsViewModel(
    private val apiService: ApiService,
    private val songRepository: SongRepository
) : ViewModel() {

    private val _state = MutableStateFlow<UnidentifiedUiState>(UnidentifiedUiState.Loading)
    val state: StateFlow<UnidentifiedUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = UnidentifiedUiState.Loading
        viewModelScope.launch {
            try {
                val songs = apiService.unidentifiedSongs()
                _state.value = UnidentifiedUiState.Ready(songs)
            } catch (ce: CancellationException) { throw ce }
            catch (t: Throwable) {
                _state.value = UnidentifiedUiState.Error(t.message ?: "Error al cargar")
            }
        }
    }

    fun loadDetails(id: Long, onReady: (SongDetailsDto) -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                onReady(songRepository.details(id))
            } catch (ce: CancellationException) { throw ce }
            catch (t: Throwable) {
                onError(t.message ?: "No se pudo cargar")
            }
        }
    }

    fun saveTags(
        id: Long,
        request: TagEditRequest,
        reEnrichAfter: Boolean,
        onDone: (ok: Boolean, msg: String) -> Unit
    ) {
        viewModelScope.launch {
            val saved = try {
                songRepository.editTags(id, request)
            } catch (ce: CancellationException) { throw ce }
            catch (t: Throwable) {
                onDone(false, t.message ?: "No se pudieron guardar los tags")
                return@launch
            }
            if (reEnrichAfter) {
                try {
                    val resp = apiService.reEnrichSong(id)
                    if (!resp.isSuccessful) {
                        onDone(false, "Tags guardados pero el reintento devolvió HTTP ${resp.code()}")
                        return@launch
                    }
                } catch (ce: CancellationException) { throw ce }
                catch (t: Throwable) {
                    onDone(false, "Tags guardados pero el reintento falló: ${t.message}")
                    return@launch
                }
            }
            onDone(true, "Tags guardados · ${saved.title}")
            kotlinx.coroutines.delay(1500)
            refresh()
        }
    }

    fun reEnrich(id: Long, onDone: (ok: Boolean, msg: String) -> Unit) {
        viewModelScope.launch {
            try {
                val resp = apiService.reEnrichSong(id)
                if (resp.isSuccessful) {
                    onDone(true, "Reintentando enriquecimiento…")
                    kotlinx.coroutines.delay(1500)
                    refresh()
                } else {
                    onDone(false, "HTTP ${resp.code()}")
                }
            } catch (ce: CancellationException) { throw ce }
            catch (t: Throwable) {
                onDone(false, t.message ?: "No se pudo reintentar")
            }
        }
    }

    companion object {
        fun factory(
            apiService: ApiService,
            songRepository: SongRepository
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                UnidentifiedSongsViewModel(apiService, songRepository) as T
        }
    }
}
