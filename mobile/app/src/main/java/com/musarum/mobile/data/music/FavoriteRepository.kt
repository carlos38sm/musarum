package com.musarum.mobile.data.music

import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.FavoriteDto
import com.musarum.mobile.data.network.dto.MarkFavoriteRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class FavoriteRepository(private val apiService: ApiService) {

    private val _favorites = MutableStateFlow<List<FavoriteDto>>(emptyList())
    val favorites: StateFlow<List<FavoriteDto>> = _favorites.asStateFlow()

    private val _favoriteIds = MutableStateFlow<Set<Long>>(emptySet())
    val favoriteIds: StateFlow<Set<Long>> = _favoriteIds.asStateFlow()

    private val mutex = Mutex()
    private var initialized = false

    suspend fun refresh(): List<FavoriteDto> = mutex.withLock {
        val list = apiService.getFavorites()
        _favorites.value = list
        _favoriteIds.value = list.map { it.songId }.toSet()
        initialized = true
        list
    }

    suspend fun ensureLoaded() {
        if (!initialized) runCatching { refresh() }
    }

    suspend fun toggle(songId: Long): Boolean = mutex.withLock {
        val wasFav = songId in _favoriteIds.value
        if (wasFav) {
            apiService.unmarkFavorite(songId)
            _favorites.value = _favorites.value.filterNot { it.songId == songId }
            _favoriteIds.value = _favoriteIds.value - songId
            false
        } else {
            val created = apiService.markFavorite(MarkFavoriteRequest(songId))
            _favorites.value = _favorites.value + created
            _favoriteIds.value = _favoriteIds.value + songId
            true
        }
    }

    fun clear() {
        _favorites.value = emptyList()
        _favoriteIds.value = emptySet()
        initialized = false
    }

    fun isFavorite(songId: Long): Boolean = songId in _favoriteIds.value
}
