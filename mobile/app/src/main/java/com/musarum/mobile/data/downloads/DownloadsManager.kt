package com.musarum.mobile.data.downloads

import android.util.Log
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.data.preferences.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

/**
 * Descarga canciones del backend y las deja en {@code filesDir/downloads/}.
 * Reutiliza el {@link OkHttpClient} del AppContainer, que ya mete el JWT, así
 * que no hace falta montar un cliente aparte.
 *
 * Flujo:
 *   1. {@link #download} lanza una coroutine en el scope de app, marca la
 *      canción en {@link #inProgress} y pide bytes a {@code /stream/{id}}.
 *   2. Los bytes se vuelcan a un fichero temporal ({@code .part}); al terminar
 *      se renombra al definitivo y se registra en el {@link DownloadsRepository}.
 *   3. Fallo o cancelación → se borra el {@code .part} para no dejar basura.
 *
 * Caducidad: {@link #cleanExpired} se llama al arrancar la app. Borra las
 * descargas cuyo {@code expiresAt < now} salvo que coincida con la canción
 * sonando ahora (se pasa como parámetro).
 */
class DownloadsManager(
    private val repository: DownloadsRepository,
    private val okHttpClient: OkHttpClient,
    private val preferences: PlayerPreferences,
    private val applicationScope: CoroutineScope,
    /** URL del backend ya resuelta (BaseUrlStore.current). */
    private val baseUrl: String
) {

    companion object {
        private const val TAG = "DownloadsManager"
        private const val TEMPORARY_DURATION_MS = 24L * 60 * 60 * 1000  // 24h
    }

    private val _inProgress = MutableStateFlow<Set<Long>>(emptySet())
    val inProgress: StateFlow<Set<Long>> = _inProgress.asStateFlow()

    // Serializa las descargas: evitamos pegar al backend con varias descargas
    // paralelas desde un mismo cliente. Si en el futuro se quieren paralelas,
    // basta con sustituir el mutex por un Semaphore(n).
    private val mutex = Mutex()

    /**
     * Encola la descarga. Si ya está descargada o descargándose, no hace nada.
     * - {@code forceKeep}: fuerza permanente aunque el toggle pida temporales.
     * - {@code forceTemporary}: fuerza temporal aunque el toggle pida permanentes.
     * Si ambos son false, se respeta el toggle del usuario. Se usan para
     * flujos específicos: el botón de NowPlaying hace ciclo "temporal →
     * permanente → ninguna" y pasa forceTemporary al descargar; el long-press
     * "mantener permanente" pasa forceKeep.
     */
    fun download(song: SongSummaryDto, forceKeep: Boolean = false, forceTemporary: Boolean = false) {
        if (repository.get(song.id) != null) return
        if (_inProgress.value.contains(song.id)) return

        applicationScope.launch {
            mutex.withLock {
                // Re-chequeamos dentro del lock por si cambió entre llamada y lock.
                if (repository.get(song.id) != null) return@withLock
                _inProgress.value = _inProgress.value + song.id
                try {
                    doDownload(song, forceKeep, forceTemporary)
                } catch (t: Throwable) {
                    Log.e(TAG, "Fallo descargando ${song.id}", t)
                } finally {
                    _inProgress.value = _inProgress.value - song.id
                }
            }
        }
    }

    private suspend fun doDownload(song: SongSummaryDto, forceKeep: Boolean, forceTemporary: Boolean) {
        withContext(Dispatchers.IO) {
            val url = baseUrl.trimEnd('/') + "/stream/" + song.id
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "HTTP ${response.code} descargando ${song.id}")
                    return@use
                }
                val body = response.body ?: run {
                    Log.w(TAG, "Body vacío descargando ${song.id}")
                    return@use
                }
                // Deducimos la extensión a partir del Content-Type del backend;
                // fallback a .mp3 por ser lo más común.
                val ext = extensionFor(response.header("Content-Type"))
                val finalFile = File(repository.root(), "${song.id}.$ext")
                val tempFile = File(repository.root(), "${song.id}.$ext.part")
                try {
                    body.byteStream().use { input ->
                        tempFile.outputStream().use { output ->
                            input.copyTo(output, bufferSize = 64 * 1024)
                        }
                    }
                    if (!tempFile.renameTo(finalFile)) {
                        tempFile.delete()
                        Log.w(TAG, "No pude mover .part a definitivo para ${song.id}")
                        return@use
                    }
                    val now = System.currentTimeMillis()
                    val temporary = when {
                        forceKeep -> false
                        forceTemporary -> true
                        else -> preferences.downloadsTemporaryByDefault.value
                    }
                    val info = DownloadInfo(
                        songId = song.id,
                        fileName = finalFile.name,
                        downloadedAt = now,
                        expiresAt = if (temporary) now + TEMPORARY_DURATION_MS else null,
                        sizeBytes = finalFile.length(),
                        title = song.title,
                        artistsLabel = song.artistsLabel
                    )
                    repository.upsert(info)
                    Log.i(TAG, "Descargada ${song.id} (${info.sizeBytes} B, temp=$temporary)")
                } catch (t: Throwable) {
                    runCatching { tempFile.delete() }
                    throw t
                }
            }
        }
    }

    /**
     * Convierte una descarga temporal en permanente (y viceversa si {@code
     * keep} es false, aunque entonces el usuario tendrá que esperar al próximo
     * cleanExpired para borrarla).
     */
    fun setKeepPermanent(songId: Long, keep: Boolean) {
        val info = repository.get(songId) ?: return
        val now = System.currentTimeMillis()
        val next = info.copy(
            expiresAt = if (keep) null else now + TEMPORARY_DURATION_MS
        )
        repository.upsert(next)
    }

    fun remove(songId: Long) = repository.remove(songId)

    fun clearAll() = repository.clearAll()

    /**
     * Borra las descargas caducadas. {@code currentlyPlayingId} se respeta:
     * si una canción ya caducó pero está sonando ahora mismo, no la tocamos
     * (se eliminará en la siguiente limpieza). Se llama al arrancar la app.
     */
    fun cleanExpired(currentlyPlayingId: Long? = null) {
        applicationScope.launch {
            val now = System.currentTimeMillis()
            repository.entries.value.values
                .filter { it.expiresAt != null && it.expiresAt < now && it.songId != currentlyPlayingId }
                .forEach {
                    Log.i(TAG, "Borrando descarga caducada ${it.songId}")
                    repository.remove(it.songId)
                }
        }
    }

    private fun extensionFor(contentType: String?): String {
        if (contentType == null) return "mp3"
        return when {
            contentType.contains("flac", ignoreCase = true) -> "flac"
            contentType.contains("ogg",  ignoreCase = true) -> "ogg"
            contentType.contains("wav",  ignoreCase = true) -> "wav"
            contentType.contains("m4a",  ignoreCase = true) ||
            contentType.contains("aac",  ignoreCase = true) -> "m4a"
            else -> "mp3"
        }
    }
}
