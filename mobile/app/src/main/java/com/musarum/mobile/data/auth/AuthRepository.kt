package com.musarum.mobile.data.auth

import com.musarum.mobile.data.TokenManager
import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.LoginRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class AuthRepository(
    private val apiService: ApiService,
    private val tokenManager: TokenManager,
    scope: CoroutineScope
) {
    val isAuthenticated: StateFlow<Boolean> = tokenManager.token
        .map { it != null }
        .stateIn(scope, SharingStarted.Eagerly, tokenManager.currentToken() != null)

    suspend fun login(nick: String, password: String) {
        val response = apiService.login(LoginRequest(nick, password))
        tokenManager.saveToken(response.token)
    }

    suspend fun logout() {
        tokenManager.clearToken()
    }
}
