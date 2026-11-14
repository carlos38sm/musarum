package com.musarum.mobile.data

import android.content.Context
import com.musarum.mobile.BuildConfig

/**
 * Permite al OWNER (o a cualquier user) sobreescribir la URL del backend
 * sin recompilar la app. Útil cuando la IP Tailscale del portátil del
 * OWNER cambia, o cuando un member entra desde una red donde la IP de
 * {@code BuildConfig.MUSARUM_BASE_URL} no es la correcta.
 *
 * La URL se guarda en SharedPreferences no encriptadas (no es secreta, es
 * un host de Tailscale). Al leer:
 *   - Si hay override guardado, lo usamos.
 *   - Si no, cae al valor baked-in del {@link BuildConfig}.
 *
 * Normalizamos siempre con "/" al final — Retrofit lo requiere.
 */
class BaseUrlStore(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences("musarum_baseurl", Context.MODE_PRIVATE)

    /** URL activa en esta sesión. Se resuelve una sola vez por AppContainer. */
    fun current(): String {
        val override = prefs.getString(KEY, null)?.takeIf { it.isNotBlank() }
        val raw = override ?: BuildConfig.MUSARUM_BASE_URL
        return ensureTrailingSlash(raw)
    }

    /** URL baked-in a modo de referencia (lo que mostrar bajo el campo). */
    fun defaultUrl(): String = ensureTrailingSlash(BuildConfig.MUSARUM_BASE_URL)

    /** true si hay un override activo (distinto del baked-in). */
    fun hasOverride(): Boolean = prefs.contains(KEY)

    /** Guarda un nuevo baseUrl. El efecto requiere reiniciar la app. */
    fun save(url: String) {
        val normalized = ensureTrailingSlash(url.trim())
        prefs.edit().putString(KEY, normalized).apply()
    }

    /** Vuelve al baked-in. */
    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private fun ensureTrailingSlash(s: String): String =
        if (s.endsWith("/")) s else "$s/"

    companion object {
        private const val KEY = "override_base_url"

        /**
         * Valida si una cadena puede usarse como baseUrl de Retrofit. Retrofit
         * exige http:// o https:// + un host no vacío + ruta acabada en "/".
         * Si no cumple, Retrofit.Builder.baseUrl lanza IllegalArgumentException
         * al construir → la app no arranca. Primera defensa: el diálogo del
         * Login la usa para no guardar basura. AppContainer tiene una segunda
         * defensa por si acaso.
         */
        fun isValid(url: String): Boolean {
            val t = url.trim()
            if (!t.startsWith("http://") && !t.startsWith("https://")) return false
            val afterScheme = t.substringAfter("://")
            val host = afterScheme.substringBefore("/").substringBefore(":")
            return host.isNotBlank() && !host.contains(" ")
        }
    }
}
