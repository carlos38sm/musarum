package com.musarum.mobile.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.FavoriteRepository
import com.musarum.mobile.data.music.SongRepository
import com.musarum.mobile.data.network.dto.SongDetailsDto
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class NowPlayingUiState(
    val details: SongDetailsDto? = null,
    val lyrics: String? = null,
    val lyricsLoading: Boolean = false,
    val lyricsError: String? = null,
    val detailsLoading: Boolean = false,
    val detailsError: String? = null,
    val isFavorite: Boolean = false
)

class NowPlayingViewModel(
    private val songRepository: SongRepository,
    private val favoriteRepository: FavoriteRepository,
    playerController: PlayerController
) : ViewModel() {

    private val _state = MutableStateFlow(NowPlayingUiState())
    val state: StateFlow<NowPlayingUiState> = _state.asStateFlow()

    private var loadJob: Job? = null

    init {
        viewModelScope.launch { favoriteRepository.ensureLoaded() }

        viewModelScope.launch {
            playerController.state
                .map { it.currentSong?.id }
                .distinctUntilChanged()
                .collect { id ->
                    if (id == null) {
                        _state.value = NowPlayingUiState()
                    } else {
                        loadFor(id)
                    }
                }
        }

        viewModelScope.launch {
            combine(
                playerController.state.map { it.currentSong?.id }.distinctUntilChanged(),
                favoriteRepository.favoriteIds
            ) { id, favs -> id != null && id in favs }
                .collect { isFav -> _state.value = _state.value.copy(isFavorite = isFav) }
        }
    }

    fun toggleFavorite() {
        val songId = _state.value.details?.id ?: return
        viewModelScope.launch { runCatching { favoriteRepository.toggle(songId) } }
    }

    private fun loadFor(songId: Long) {
        loadJob?.cancel()
        _state.value = NowPlayingUiState(
            detailsLoading = true,
            isFavorite = favoriteRepository.isFavorite(songId)
        )
        loadJob = viewModelScope.launch {
            runCatching { songRepository.details(songId) }
                .onSuccess { details ->
                    _state.value = _state.value.copy(
                        details = details,
                        detailsLoading = false,
                        detailsError = null
                    )
                    if (details.hasLyrics) loadLyrics(songId)
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        detailsLoading = false,
                        detailsError = e.message ?: "Error"
                    )
                }
        }
    }

    private suspend fun loadLyrics(songId: Long) {
        _state.value = _state.value.copy(lyricsLoading = true, lyricsError = null, lyrics = null)
        runCatching { songRepository.lyrics(songId) }
            .onSuccess { text ->
                val cleaned = cleanLrc(text)
                _state.value = _state.value.copy(
                    // Cuerpo vacío = backend no encontró letra → UI muestra "Sin letra"
                    lyrics = cleaned.ifBlank { null },
                    lyricsLoading = false
                )
            }
            .onFailure { e ->
                _state.value = _state.value.copy(
                    lyricsLoading = false,
                    lyricsError = e.message ?: "Error"
                )
            }
    }

    /**
     * Fuerza al backend a re-buscar la letra online ignorando lo cacheado.
     * Si encuentra nueva, la mostramos; si no, mantenemos la que había
     * (el backend responde 204 y el repo devuelve null).
     * Devuelve el callback onFinished(found, message) para que la UI
     * muestre un snackbar apropiado.
     */
    /**
     * Patch de tags ID3 + metadatos. En éxito actualizamos el {@code details}
     * del state con lo devuelto por el backend para que la InfoScreen pinte
     * los nuevos valores sin recargar. Si el patch incluía renameFile y el
     * backend lo hizo, el nuevo audio_path llega en el details.
     */
    fun editTags(
        request: com.musarum.mobile.data.network.dto.TagEditRequest,
        onFinished: (Boolean, String) -> Unit
    ) {
        val songId = _state.value.details?.id ?: return
        viewModelScope.launch {
            runCatching { songRepository.editTags(songId, request) }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(details = updated)
                    onFinished(true, "Tags actualizados")
                }
                .onFailure { e ->
                    onFinished(false, e.message ?: "No se pudo actualizar")
                }
        }
    }

    fun refreshLyrics(onFinished: (Boolean, String) -> Unit) {
        val songId = _state.value.details?.id ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(lyricsLoading = true, lyricsError = null)
            runCatching { songRepository.refreshLyrics(songId) }
                .onSuccess { text ->
                    if (text != null) {
                        val cleaned = cleanLrc(text)
                        _state.value = _state.value.copy(
                            lyrics = cleaned.ifBlank { null },
                            lyricsLoading = false
                        )
                        onFinished(true, "Letra actualizada")
                    } else {
                        _state.value = _state.value.copy(lyricsLoading = false)
                        onFinished(false, "No se encontró otra letra")
                    }
                }
                .onFailure { e ->
                    _state.value = _state.value.copy(
                        lyricsLoading = false,
                        lyricsError = e.message ?: "Error"
                    )
                    onFinished(false, "Error buscando letra")
                }
        }
    }

    /**
     * No destripa los timestamps: solo quita las líneas meta ([ar:...], [ti:...])
     * para que el parser sincronizado pueda construir pares (ms, texto).
     * Si LrcLib devolvió plain lyrics sin timestamps, también se preserva tal cual.
     */
    private fun cleanLrc(raw: String): String {
        val meta = Regex("""^\[[a-zA-Z]+:[^\]]*\]\s*$""")
        return raw.lineSequence()
            .filterNot { meta.matches(it.trim()) }
            .joinToString("\n")
            .trim()
    }

    companion object {
        fun factory(
            songRepository: SongRepository,
            favoriteRepository: FavoriteRepository,
            playerController: PlayerController
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                NowPlayingViewModel(songRepository, favoriteRepository, playerController) as T
        }
    }
}
