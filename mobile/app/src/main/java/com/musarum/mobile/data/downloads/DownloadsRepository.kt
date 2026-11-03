package com.musarum.mobile.data.downloads

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Lee y escribe el índice de descargas (filesDir/downloads/index.json) y
 * expone el mapa {songId → DownloadInfo} como StateFlow para que la UI se
 * refresque cuando aparezcan/desaparezcan descargas.
 *
 * Thread-safety: sincroniza la escritura al JSON con un lock interno. Los
 * lectores miran el StateFlow, que ya es seguro multi-hilo.
 */
class DownloadsRepository(context: Context) {

    private val root: File = File(context.filesDir, "downloads").apply { mkdirs() }
    private val indexFile: File = File(root, "index.json")
    private val gson = Gson()
    private val writeLock = Any()

    private val _entries = MutableStateFlow(loadFromDisk())
    val entries: StateFlow<Map<Long, DownloadInfo>> = _entries.asStateFlow()

    /** Carpeta donde viven los ficheros descargados. */
    fun root(): File = root

    /** Fichero local de una canción descargada, o null si no está (o se perdió). */
    fun fileFor(songId: Long): File? {
        val entry = _entries.value[songId] ?: return null
        val f = File(root, entry.fileName)
        return if (f.exists()) f else null
    }

    fun get(songId: Long): DownloadInfo? = _entries.value[songId]

    fun totalSizeBytes(): Long = _entries.value.values.sumOf { it.sizeBytes }

    fun upsert(info: DownloadInfo) {
        synchronized(writeLock) {
            val next = _entries.value.toMutableMap()
            next[info.songId] = info
            _entries.value = next
            persist(next)
        }
    }

    /**
     * Borra el registro del índice y el fichero del disco. Idempotente: si no
     * existía, no pasa nada.
     */
    fun remove(songId: Long) {
        synchronized(writeLock) {
            val next = _entries.value.toMutableMap()
            val removed = next.remove(songId) ?: return
            _entries.value = next
            runCatching { File(root, removed.fileName).delete() }
            persist(next)
        }
    }

    /** Borra todas las descargas y vacía el índice. */
    fun clearAll() {
        synchronized(writeLock) {
            _entries.value.values.forEach {
                runCatching { File(root, it.fileName).delete() }
            }
            _entries.value = emptyMap()
            persist(emptyMap())
        }
    }

    private fun loadFromDisk(): Map<Long, DownloadInfo> {
        if (!indexFile.exists()) return emptyMap()
        return runCatching {
            val type = object : TypeToken<List<DownloadInfo>>() {}.type
            val list: List<DownloadInfo> = gson.fromJson(indexFile.readText(), type) ?: emptyList()
            list.associateBy { it.songId }
        }.getOrElse {
            Log.w("DownloadsRepository", "No pude leer index.json, reinicio vacío", it)
            emptyMap()
        }
    }

    private fun persist(map: Map<Long, DownloadInfo>) {
        runCatching {
            indexFile.writeText(gson.toJson(map.values.toList()))
        }.onFailure {
            Log.e("DownloadsRepository", "No pude escribir index.json", it)
        }
    }
}
