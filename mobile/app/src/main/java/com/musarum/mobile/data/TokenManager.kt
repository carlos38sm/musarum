package com.musarum.mobile.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

private val Context.musarumDataStore by preferencesDataStore(name = "musarum_prefs")

class TokenManager(
    context: Context,
    scope: CoroutineScope
) {
    private val dataStore = context.applicationContext.musarumDataStore

    // El StateFlow lo gestionamos nosotros (no vía stateIn) para que el
    // write a saveToken/clearToken refleje el nuevo valor EN EL ACTO —
    // sin esperar a que DataStore propague el cambio por su cuenta.
    //
    // RACE QUE ARREGLA: antes `dataStore.data.stateIn(...)` tenía un lag
    // de unos ms entre el `edit {}` que completaba y el StateFlow emitir
    // el nuevo valor. El Home disparaba 7 peticiones paralelas justo al
    // login; alguna salía antes de ese lag, el AuthInterceptor leía
    // `token.value == null`, enviaba sin Authorization, el backend
    // respondía 401, UnauthorizedInterceptor limpiaba el token → vuelta
    // al Login aunque la clave era correcta.
    private val _token = MutableStateFlow<String?>(null)
    val token: StateFlow<String?> = _token.asStateFlow()

    init {
        // Primera lectura SÍNCRONA para que al construir AppContainer ya
        // tengamos el token guardado del disco en memoria. Sin esto, al
        // relanzar la app con sesión abierta, NavHost puede evaluar
        // `isAuthenticated=false` y mandar un segundo al Login antes de
        // que DataStore emita por primera vez.
        _token.value = runBlocking {
            dataStore.data.first()[TOKEN_KEY]
        }
        // Después seguimos observando por si algo externo cambia el valor
        // (p. ej. clearToken desde otro collector).
        scope.launch {
            dataStore.data
                .map { it[TOKEN_KEY] }
                .collect { _token.value = it }
        }
    }

    fun currentToken(): String? = _token.value

    suspend fun saveToken(value: String) {
        // ORDEN CLAVE: actualizamos el StateFlow ANTES del write al DataStore
        // para que al retornar esta suspend, AuthInterceptor.currentToken()
        // ya devuelva el nuevo token.
        _token.value = value
        dataStore.edit { it[TOKEN_KEY] = value }
    }

    suspend fun clearToken() {
        _token.value = null
        dataStore.edit { it.remove(TOKEN_KEY) }
    }

    private companion object {
        val TOKEN_KEY = stringPreferencesKey("auth_token")
    }
}
