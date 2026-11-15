package com.musarum.mobile.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Almacena el JWT de la sesión del usuario. SharedPreferences síncrono —
 * lecturas inmediatas, sin coroutines, sin riesgo de ANR en el main thread.
 *
 * ANTES: usábamos {@link androidx.datastore.preferences.PreferenceDataStore}.
 * El acceso es asíncrono por diseño: {@code dataStore.data.first()} suspend.
 * Para que el {@link AuthInterceptor} pudiera leer el token SÍNCRONO justo
 * tras un {@code saveToken}, metíamos un {@code runBlocking} en el init.
 * Resultado: bloqueaba el main thread al arrancar la app, con riesgo de
 * ANR → "la app no se instala bien" en algunos dispositivos.
 *
 * SharedPreferences no tiene ese problema: {@code getString()} es un read
 * sobre un map en memoria (cargado por el framework antes de onCreate),
 * y {@code edit().apply()} escribe en disco en background sin bloquear.
 * Es exactamente lo que queríamos.
 *
 * COSTE DE LA MIGRACIÓN: los usuarios que ya tengan token guardado en
 * DataStore van a perder la sesión al actualizar y tendrán que volver a
 * loguear una vez. Es mal menor frente al ANR en arranque.
 */
@Suppress("UNUSED_PARAMETER")
class TokenManager(
    context: Context,
    scope: CoroutineScope  // mantenido por compat de firma; no se usa.
) {
    private val prefs = context.applicationContext
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    // Flow reactivo para que `AuthRepository.isAuthenticated` observe cambios
    // (Login, Logout, Unauthorized → clear). Lo inicializamos con el valor
    // ya persistido para que al arrancar con sesión abierta el NavHost vea
    // `isAuthenticated=true` desde el primer frame (sin pasar por Login).
    private val _token = MutableStateFlow<String?>(prefs.getString(TOKEN_KEY, null))
    val token: StateFlow<String?> = _token.asStateFlow()

    fun currentToken(): String? = _token.value

    suspend fun saveToken(value: String) {
        // Orden clave: StateFlow ANTES del write a disco. Tras retornar esta
        // suspend, AuthInterceptor.currentToken() ya devuelve el nuevo token;
        // sin esto el Home disparaba 7 peticiones paralelas en el race y
        // alguna salía sin Authorization → 401 → vuelta al Login.
        _token.value = value
        prefs.edit().putString(TOKEN_KEY, value).apply()
    }

    suspend fun clearToken() {
        _token.value = null
        prefs.edit().remove(TOKEN_KEY).apply()
    }

    private companion object {
        const val PREF_NAME = "musarum_auth"
        const val TOKEN_KEY = "auth_token"
    }
}
