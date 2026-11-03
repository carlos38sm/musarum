package com.musarum.mobile.session

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.musarum.mobile.BuildConfig
import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.VersionDto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Comprueba al arrancar si hay una APK más reciente que la instalada. Si
 * la hay, expone un {@link StateFlow<AvailableUpdate?>} que el shell del
 * NavHost observa y pinta un diálogo "Actualizar". Si el backend no tiene
 * configurado el canal (latestApk == null) o la versión instalada ya es
 * igual/mayor, el flow queda null y nadie se entera.
 */
class UpdateChecker(
    private val apiService: ApiService,
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
    }

    fun openDownload(context: Context) {
        val url = _available.value?.url ?: return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
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
