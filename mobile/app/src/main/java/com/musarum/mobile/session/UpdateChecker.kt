package com.musarum.mobile.session

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.musarum.mobile.BuildConfig
import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.VersionDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * Comprueba al arrancar si hay una APK más reciente que la instalada. Si
 * la hay, expone un {@link StateFlow<AvailableUpdate?>} que el shell del
 * NavHost observa y pinta un diálogo "Actualizar". Si el backend no tiene
 * configurado el canal (latestApk == null) o la versión instalada ya es
 * igual/mayor, el flow queda null y nadie se entera.
 *
 * Salvaguarda contra el "bucle de actualización rota" (nov-25 2026):
 * si el OWNER publica una versión antes de que exista la release en
 * GitHub (CI rojo, etc.) y la marca obligatoria, los usuarios quedan
 * atrapados — el diálogo no se cierra y "Descargar" lleva a un 404.
 * Para evitarlo, {@link openDownload} hace primero un HEAD al URL; si
 * no responde 2xx, marca {@link downloadMissing} y el diálogo cambia
 * de forma: deja de ser bloqueante, muestra mensaje de "no disponible"
 * y ofrece cerrar.
 */
class UpdateChecker(
    private val apiService: ApiService,
    private val okHttpClient: OkHttpClient,
    private val applicationScope: CoroutineScope
) {

    data class AvailableUpdate(
        val latest: String,
        val current: String,
        val url: String,
        val mandatory: Boolean
    )

    private val _available = MutableStateFlow<AvailableUpdate?>(null)
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    /** True cuando se detectó que el URL del APK no está accesible
     *  (típicamente 404 porque la release no se publicó aún / falló el
     *  CI). El NavHost usa esto para desactivar el modo mandatory. */
    private val _downloadMissing = MutableStateFlow(false)
    val downloadMissing: StateFlow<Boolean> = _downloadMissing.asStateFlow()

    @Volatile private var dismissed: Boolean = false

    fun checkOnce() {
        if (dismissed) return
        applicationScope.launch {
            runCatching { apiService.appVersion() }
                .onSuccess { dto -> evaluate(dto) }
                .onFailure { Log.d("UpdateChecker", "No pude leer /version: ${it.message}") }
        }
    }

    fun dismiss() {
        dismissed = true
        _available.value = null
        _downloadMissing.value = false
    }

    /**
     * Comprueba primero con un HEAD que el URL esté vivo. Si lo está,
     * abre el navegador con el Intent. Si no, marca {@link downloadMissing}
     * para que el diálogo deje de ser bloqueante.
     *
     * HEAD en Dispatchers.IO (requerido por OkHttp sync). No usamos el
     * resultado para nada más — solo saber si el usuario va a aterrizar
     * en un 404.
     */
    fun openDownload(context: Context) {
        val url = _available.value?.url ?: return
        applicationScope.launch {
            val reachable = runCatching {
                withContext(Dispatchers.IO) {
                    val req = Request.Builder().url(url).head().build()
                    okHttpClient.newCall(req).execute().use { it.isSuccessful }
                }
            }.getOrDefault(false)
            if (!reachable) {
                Log.w("UpdateChecker", "URL de actualización no accesible: $url")
                _downloadMissing.value = true
                return@launch
            }
            runCatching {
                context.startActivity(
                    Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        }
    }

    private fun evaluate(dto: VersionDto) {
        val latest = dto.latestApk?.trim().orEmpty()
        val url = dto.apkUrl?.trim().orEmpty()
        if (latest.isBlank() || url.isBlank()) return
        val current = BuildConfig.VERSION_NAME
        if (compareSemver(latest, current) <= 0) return
        _available.value = AvailableUpdate(latest, current, url, dto.mandatory)
    }

    private fun compareSemver(a: String, b: String): Int {
        return runCatching {
            val ap = a.split(".").map { it.toInt() }
            val bp = b.split(".").map { it.toInt() }
            for (i in 0 until maxOf(ap.size, bp.size)) {
                val av = ap.getOrElse(i) { 0 }
                val bv = bp.getOrElse(i) { 0 }
                if (av != bv) return@runCatching av - bv
            }
            0
        }.getOrElse { a.compareTo(b) }
    }
}
