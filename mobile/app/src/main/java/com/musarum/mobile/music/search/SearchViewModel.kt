package com.musarum.mobile.music.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.SearchRepository
import com.musarum.mobile.data.network.dto.SearchResultDto
import com.musarum.mobile.data.preferences.PlayerPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class SearchFilter { ALL, ARTISTS, ALBUMS, SONGS }

sealed class SearchUiState {
    data object Empty : SearchUiState()
    data object Loading : SearchUiState()
    data class Ready(val result: SearchResultDto) : SearchUiState()
    data class Error(val message: String) : SearchUiState()
}

class SearchViewModel(
    private val repository: SearchRepository,
    private val preferences: PlayerPreferences
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _state = MutableStateFlow<SearchUiState>(SearchUiState.Empty)
    val state: StateFlow<SearchUiState> = _state.asStateFlow()

    private val _filter = MutableStateFlow(SearchFilter.ALL)
    val filter: StateFlow<SearchFilter> = _filter.asStateFlow()

    val recentSearches: StateFlow<List<String>> = preferences.recentSearches

    private var searchJob: Job? = null

    fun onQueryChange(q: String) {
        _query.value = q
        searchJob?.cancel()
        if (q.isBlank()) {
            _state.value = SearchUiState.Empty
            return
        }
        searchJob = viewModelScope.launch {
            delay(300)
            _state.value = SearchUiState.Loading
            try {
                val result = repository.search(q)
                _state.value = SearchUiState.Ready(result)
                // IMPORTANTE: NO guardamos la query aquí. Antes lo hacíamos
                // y se persistían todos los pasos intermedios ("Ba", "Bad",
                // "Bad r", "Bad ro"...) en los recientes. Ahora solo se
                // guarda cuando el usuario pulsa "Buscar" en el teclado
                // (commitSearch) o toca un resultado (onResultTapped).
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = SearchUiState.Error(t.message ?: "Error")
            }
        }
    }

    /** Llamado al pulsar la lupa / "Buscar" del teclado. Guarda la query
     *  actual como búsqueda reciente si tiene al menos 2 caracteres. */
    fun commitSearch() {
        val q = _query.value.trim()
        if (q.length >= 2) preferences.pushRecentSearch(q)
    }

    /** Llamado cuando el usuario toca un resultado (canción, álbum, artista).
     *  Es la señal más fiable de que la búsqueda fue "útil"; la guardamos. */
    fun onResultTapped() = commitSearch()

    fun setFilter(f: SearchFilter) { _filter.value = f }

    fun clearRecent() = preferences.clearRecentSearches()

    companion object {
        fun factory(
            repository: SearchRepository,
            preferences: PlayerPreferences
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    SearchViewModel(repository, preferences) as T
            }
    }
}
