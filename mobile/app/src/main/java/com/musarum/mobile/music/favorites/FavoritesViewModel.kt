package com.musarum.mobile.music.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.music.FavoriteRepository
import com.musarum.mobile.data.network.dto.FavoriteDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class FavoritesUiState {
    data object Loading : FavoritesUiState()
    data class Ready(val favorites: List<FavoriteDto>) : FavoritesUiState()
    data class Error(val message: String) : FavoritesUiState()
}

class FavoritesViewModel(private val repository: FavoriteRepository) : ViewModel() {

    private val _state = MutableStateFlow<FavoritesUiState>(FavoritesUiState.Loading)
    val state: StateFlow<FavoritesUiState> = _state.asStateFlow()

    private val _refreshing = MutableStateFlow(false)
    val refreshing: StateFlow<Boolean> = _refreshing.asStateFlow()

    init {
        viewModelScope.launch {
            repository.favorites.collect { list ->
                if (_state.value !is FavoritesUiState.Loading || list.isNotEmpty()) {
                    _state.value = FavoritesUiState.Ready(list)
                }
            }
        }
        refresh()
    }

    fun refresh() {
        _refreshing.value = true
        viewModelScope.launch {
            if (_state.value !is FavoritesUiState.Ready) {
                _state.value = FavoritesUiState.Loading
            }
            runCatching { repository.refresh() }
                .onFailure { e ->
                    if (_state.value !is FavoritesUiState.Ready) {
                        _state.value = FavoritesUiState.Error(e.message ?: "Error")
                    }
                }
            _refreshing.value = false
        }
    }

    fun toggle(songId: Long) {
        viewModelScope.launch { runCatching { repository.toggle(songId) } }
    }

    companion object {
        fun factory(repository: FavoriteRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    FavoritesViewModel(repository) as T
            }
    }
}
