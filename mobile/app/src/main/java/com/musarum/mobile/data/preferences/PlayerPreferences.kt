package com.musarum.mobile.data.preferences

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Preferencias ligeras del reproductor persistidas en SharedPreferences. Hoy
 * solo guarda la duración del crossfade; cuando añadamos EQ, gapless, etc.
 * se amplía aquí.
 */
class PlayerPreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    private val _crossfadeSeconds = MutableStateFlow(
        prefs.getInt(KEY_CROSSFADE_SECONDS, 0)
    )
    val crossfadeSeconds: StateFlow<Int> = _crossfadeSeconds.asStateFlow()

    // ReplayGain: normalización automática de volumen entre canciones.
    // Por defecto ON — es lo que un usuario esperaría. Si alguna canción
    // tiene gain mal calculado y resulta demasiado baja, el usuario puede
    // apagarlo en Ajustes y escuchar al volumen crudo del fichero.
    private val _replayGainEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_REPLAYGAIN, true)
    )
    val replayGainEnabled: StateFlow<Boolean> = _replayGainEnabled.asStateFlow()

    // Descargas offline: si está ON, cada descarga nace temporal (caduca a
    // las 24h). El usuario puede marcar canciones concretas como permanentes
    // desde el long-press del SongRow o desde Perfil. Si está OFF, cada
    // descarga nace permanente y solo se borra manualmente.
    private val _downloadsTemporaryByDefault = MutableStateFlow(
        prefs.getBoolean(KEY_DOWNLOADS_TEMPORARY, true)
    )
    val downloadsTemporaryByDefault: StateFlow<Boolean> = _downloadsTemporaryByDefault.asStateFlow()

    // Ecualizador: lista de 5 ganancias (en milibelios, rango típico ±1500 mB).
    // Guardado como "g0,g1,g2,g3,g4" para simplificar. Si está desactivado,
    // las bandas se ignoran al aplicarlo en PlayerService.
    private val _equalizerEnabled = MutableStateFlow(
        prefs.getBoolean(KEY_EQ_ENABLED, false)
    )
    val equalizerEnabled: StateFlow<Boolean> = _equalizerEnabled.asStateFlow()

    private val _equalizerBands = MutableStateFlow(loadBands())
    val equalizerBands: StateFlow<List<Int>> = _equalizerBands.asStateFlow()

    private val _recentSearches = MutableStateFlow(loadRecent())
    val recentSearches: StateFlow<List<String>> = _recentSearches.asStateFlow()

    fun setCrossfadeSeconds(value: Int) {
        val clamped = value.coerceIn(0, 12)
        prefs.edit().putInt(KEY_CROSSFADE_SECONDS, clamped).apply()
        _crossfadeSeconds.value = clamped
    }

    fun setReplayGainEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_REPLAYGAIN, enabled).apply()
        _replayGainEnabled.value = enabled
    }

    fun setDownloadsTemporaryByDefault(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_DOWNLOADS_TEMPORARY, enabled).apply()
        _downloadsTemporaryByDefault.value = enabled
    }

    fun setEqualizerEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_EQ_ENABLED, enabled).apply()
        _equalizerEnabled.value = enabled
    }

    /** Fija las 5 ganancias en milibelios. Se recortan a [-1500, +1500]. */
    fun setEqualizerBands(bands: List<Int>) {
        val clamped = bands.map { it.coerceIn(-1500, 1500) }.take(5)
        val padded = clamped + List(5 - clamped.size) { 0 }
        prefs.edit().putString(KEY_EQ_BANDS, padded.joinToString(",")).apply()
        _equalizerBands.value = padded
    }

    private fun loadBands(): List<Int> {
        val raw = prefs.getString(KEY_EQ_BANDS, null) ?: return List(5) { 0 }
        return runCatching {
            raw.split(",").map { it.trim().toInt() }.take(5).let {
                it + List(5 - it.size) { 0 }
            }
        }.getOrElse { List(5) { 0 } }
    }

    fun pushRecentSearch(query: String) {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return
        val current = _recentSearches.value
        // Move-to-front sin duplicados; cap a 10.
        val next = (listOf(trimmed) + current.filter { !it.equals(trimmed, ignoreCase = true) })
            .take(10)
        _recentSearches.value = next
        prefs.edit().putString(KEY_RECENT_SEARCHES, next.joinToString(SEP)).apply()
    }

    fun clearRecentSearches() {
        _recentSearches.value = emptyList()
        prefs.edit().remove(KEY_RECENT_SEARCHES).apply()
    }

    private fun loadRecent(): List<String> =
        prefs.getString(KEY_RECENT_SEARCHES, null)
            ?.split(SEP)
            ?.filter { it.isNotBlank() }
            ?: emptyList()

    companion object {
        private const val FILE = "musarum_player_prefs"
        private const val KEY_CROSSFADE_SECONDS = "crossfade_seconds"
        private const val KEY_REPLAYGAIN = "replaygain_enabled"
        private const val KEY_DOWNLOADS_TEMPORARY = "downloads_temporary_default"
        private const val KEY_EQ_ENABLED = "equalizer_enabled"
        private const val KEY_EQ_BANDS = "equalizer_bands"
        private const val KEY_RECENT_SEARCHES = "recent_searches"
        // Separador que no aparece en nicks/títulos razonables.
        private const val SEP = "\u001E"
    }
}
