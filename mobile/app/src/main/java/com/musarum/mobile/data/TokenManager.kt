package com.musarum.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.musarumDataStore by preferencesDataStore(name = "musarum_prefs")

class TokenManager(
    context: Context,
    scope: CoroutineScope
) {
    private val dataStore = context.applicationContext.musarumDataStore

    val token: StateFlow<String?> = dataStore.data
        .map { it[TOKEN_KEY] }
        .stateIn(scope, SharingStarted.Eagerly, null)

    fun currentToken(): String? = token.value

    suspend fun saveToken(value: String) {
        dataStore.edit { it[TOKEN_KEY] = value }
    }

    suspend fun clearToken() {
        dataStore.edit { it.remove(TOKEN_KEY) }
    }

    private companion object {
        val TOKEN_KEY = stringPreferencesKey("auth_token")
    }
}
