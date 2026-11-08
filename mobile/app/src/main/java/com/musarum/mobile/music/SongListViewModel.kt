package com.musarum.mobile.music

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.SongRepository
import com.musarum.mobile.data.network.dto.SongSummaryDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Lista "sparse" de canciones: `songs[i]` es la canción en posición absoluta
 * `i` ordenada por título, o `null` si aún no se ha cargado la página que
 * la contiene. El móvil renderiza placeholders para los null y pide la
 * página correspondiente cuando el usuario se acerca.
 *
 * - `totalCount` lo trae el backend en el primer PageDto (totalElements).
 * - `letterOffsets` mapea letra → índice absoluto para el AlphabetJumpBar.
 * - `loadedPages` evita pedir la misma página dos veces.
 */
sealed class SongsUiState {
    data object Loading : SongsUiState()
    data class Ready(
        val songs: List<SongSummaryDto?>,
        val totalCount: Int,
        val letterOffsets: Map<String, Int>
    ) : SongsUiState()
    data class Error(val message: String) : SongsUiState()
}

class SongListViewModel(
    private val repository: SongRepository
) : ViewModel() {

    companion object {
        const val PAGE_SIZE = 40
        fun factory(repository: SongRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SongListViewModel(repository) as T
            }
    }

    private val _state = MutableStateFlow<SongsUiState>(SongsUiState.Loading)
    val state: StateFlow<SongsUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    private val _shuffleBuilding = MutableStateFlow(false)
    val shuffleBuilding: StateFlow<Boolean> = _shuffleBuilding.asStateFlow()

    // Páginas ya pedidas (success o en curso). Evita duplicar peticiones.
    private val loadedPages = mutableSetOf<Int>()

    init { refresh() }

    fun loadShuffled(onReady: (List<SongSummaryDto>) -> Unit) {
        if (_shuffleBuilding.value) return
        _shuffleBuilding.value = true
        viewModelScope.launch {
            val result = runCatching { repository.shuffled() }
            _shuffleBuilding.value = false
            result.onSuccess(onReady)
        }
    }

    fun refresh() {
        _refreshing.value = true
        if (_state.value !is SongsUiState.Ready) _state.value = SongsUiState.Loading
        loadedPages.clear()
        viewModelScope.launch {
            try {
                val first = repository.page(0, PAGE_SIZE)
                val offsets = runCatching { repository.letterOffsets() }
                    .getOrDefault(emptyMap())
                val total = first.totalElements.toInt()
                val arr = arrayOfNulls<SongSummaryDto>(total)
                first.content.forEachIndexed { idx, song -> arr[idx] = song }
                loadedPages.add(0)
                _state.value = SongsUiState.Ready(
                    songs = arr.toList(),
                    totalCount = total,
                    letterOffsets = offsets
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                if (_state.value !is SongsUiState.Ready) {
                    _state.value = SongsUiState.Error(t.message ?: "No se pudo cargar")
                }
            } finally {
                _refreshing.value = false
            }
        }
    }

    /**
     * Borra una canción (OWNER) y la saca de la lista local sin recargar
     * toda la paginación. Marca el hueco como null para que la fila
     * desaparezca sin dejar un placeholder; el próximo refresh real
     * reajustará totales y offsets.
     */
    fun deleteSong(songId: Long, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.delete(songId) }
                .onSuccess {
                    val current = _state.value as? SongsUiState.Ready
                    if (current != null) {
                        val updated = current.songs.map {
                            if (it?.id == songId) null else it
                        }
                        _state.value = current.copy(songs = updated)
                    }
                    onDone(true, "Canción eliminada")
                }
                .onFailure { onDone(false, it.message ?: "No se pudo borrar") }
        }
    }

    /**
     * Localiza una canción (por id) en la lista: carga la página que la
     * contiene si hiciera falta y llama a onFound con el índice absoluto.
     * Si la canción no existe (404 u otro error) llama a onMissing.
     * Fast-path: si ya está en la lista cargada, devuelve el índice sin
     * ir al backend.
     */
    fun locateSong(songId: Long, onFound: (Int) -> Unit, onMissing: (String) -> Unit) {
        val current = _state.value as? SongsUiState.Ready
        if (current != null) {
            val cached = current.songs.indexOfFirst { it?.id == songId }
            if (cached >= 0) {
                android.util.Log.d("Musarum/FAB", "locateSong($songId) fast-path → $cached")
                onFound(cached)
                return
            }
        }
        viewModelScope.launch {
            try {
                val position = repository.positionOf(songId).toInt()
                val total = (current?.totalCount ?: Int.MAX_VALUE)
                android.util.Log.d(
                    "Musarum/FAB",
                    "locateSong($songId) backend → position=$position total=$total"
                )
                if (position < 0 || position >= total) {
                    onMissing("Posición fuera de rango ($position/$total)")
                    return@launch
                }
                val page = position / PAGE_SIZE
                if (current != null && loadedPages.add(page)) {
                    runCatching { repository.page(page, PAGE_SIZE) }
                        .onSuccess { pageDto ->
                            val existing = _state.value as? SongsUiState.Ready
                                ?: return@onSuccess
                            val updated = existing.songs.toMutableList()
                            val start = page * PAGE_SIZE
                            pageDto.content.forEachIndexed { i, song ->
                                val abs = start + i
                                if (abs < updated.size) updated[abs] = song
                            }
                            _state.value = existing.copy(songs = updated)
                        }
                        .onFailure { loadedPages.remove(page) }
                }
                onFound(position)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                android.util.Log.w("Musarum/FAB", "locateSong($songId) error: ${t.message}", t)
                val msg = when (t) {
                    is retrofit2.HttpException ->
                        if (t.code() == 404) "El servidor no tiene esta canción"
                        else "El servidor respondió ${t.code()}"
                    is java.net.SocketTimeoutException -> "El servidor tardó demasiado"
                    is java.io.IOException -> "Sin conexión con el servidor"
                    else -> t.message ?: "Error desconocido"
                }
                onMissing(msg)
            }
        }
    }

    /**
     * Garantiza que la página que contiene el índice absoluto esté cargada.
     * Idempotente: si ya está pedida, no hace nada. Al terminar, actualiza
     * el estado con las canciones de esa página en su posición absoluta.
     */
    fun ensurePageLoadedFor(absoluteIndex: Int) {
        val current = _state.value as? SongsUiState.Ready ?: return
        if (absoluteIndex !in 0 until current.totalCount) return
        val page = absoluteIndex / PAGE_SIZE
        if (!loadedPages.add(page)) return
        viewModelScope.launch {
            runCatching { repository.page(page, PAGE_SIZE) }
                .onSuccess { pageDto ->
                    val existing = _state.value as? SongsUiState.Ready ?: return@onSuccess
                    val updated = existing.songs.toMutableList()
                    val start = page * PAGE_SIZE
                    pageDto.content.forEachIndexed { i, song ->
                        val absolute = start + i
                        if (absolute < updated.size) updated[absolute] = song
                    }
                    _state.value = existing.copy(songs = updated)
                }
                .onFailure {
                    // Fallamos silencioso pero sacamos del set para reintento.
                    loadedPages.remove(page)
                }
        }
    }
}
