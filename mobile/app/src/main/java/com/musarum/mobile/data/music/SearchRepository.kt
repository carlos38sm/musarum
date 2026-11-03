package com.musarum.mobile.data.music

import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.SearchResultDto

class SearchRepository(private val apiService: ApiService) {
    suspend fun search(query: String): SearchResultDto = apiService.search(query)
}
