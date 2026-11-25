package com.musarum.mobile.data.uploads

import android.content.Context
import android.net.Uri
import com.musarum.mobile.data.network.ApiService
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.buffer
import okio.source

enum class UploadStatus { Pending, Uploading, Done, Error }

data class UploadItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val status: UploadStatus = UploadStatus.Pending,
    val message: String? = null
)

/**
 * Gestor de subida de canciones promovido al scope de la Application.
 *
 * Antes la lista + el Job de subida vivían en el ViewModel de
 * UploadSongsScreen; al salir a Biblioteca (o cualquier otra ruta que
 * sacase la pantalla del back stack) el VM moría y:
 *   - La lista desaparecía entera (queja de Carlos: subió 21 de 42,
 *     salió para probar algo, al volver no quedaba nada).
 *   - La subida en curso se cancelaba.
 *
 * Ahora el manager vive en {@code AppContainer} y la pantalla solo lee
 * sus flows. Volver a entrar re-sincroniza con el estado real.
 */
class UploadsManager(
    private val context: Context,
    private val apiService: ApiService,
    private val applicationScope: CoroutineScope
) {
    private val _items = MutableStateFlow<List<UploadItem>>(emptyList())
    val items: StateFlow<List<UploadItem>> = _items.asStateFlow()

    private val _uploading = MutableStateFlow(false)
    val uploading: StateFlow<Boolean> = _uploading.asStateFlow()

    private val _events = MutableSharedFlow<String>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<String> = _events.asSharedFlow()

    private var nextId = 0L
    private var uploadJob: Job? = null

    fun addFiles(picked: List<PickedFile>) {
        val newItems = picked.map {
            UploadItem(
                id = ++nextId,
                uri = it.uri,
                displayName = it.displayName,
                sizeBytes = it.sizeBytes
            )
        }
        _items.value = _items.value + newItems
    }

    /** Elimina una entrada de la lista si NO está subiéndose ahora mismo.
     *  Permitido para Pending, Done y Error — una Uploading se tendría
     *  que cancelar, y eso no lo soportamos (rara vez el user quiere
     *  abortar una subida a mitad). */
    fun remove(id: Long): Boolean {
        val target = _items.value.firstOrNull { it.id == id } ?: return false
        if (target.status == UploadStatus.Uploading) return false
        _items.value = _items.value.filterNot { it.id == id }
        return true
    }

    /** Vacía la lista. Solo cuando no hay nada en curso. */
    fun clearAll() {
        if (_uploading.value) return
        _items.value = emptyList()
    }

    fun startUpload() {
        if (_uploading.value) return
        uploadJob = applicationScope.launch {
            _uploading.value = true
            try {
                val snapshot = _items.value.filter { it.status == UploadStatus.Pending }
                var okCount = 0
                var errCount = 0
                for (item in snapshot) {
                    // Si el usuario ha quitado el item mientras iba la
                    // cola, saltamos (remove() lo habrá sacado de la lista).
                    val stillThere = _items.value.any { it.id == item.id }
                    if (!stillThere) continue
                    update(item.id) { it.copy(status = UploadStatus.Uploading, message = null) }
                    try {
                        val part = buildMultipart(item)
                        val resp = apiService.uploadSong(part)
                        val body = resp.body()
                        if (resp.isSuccessful && body != null) {
                            update(item.id) {
                                it.copy(
                                    status = UploadStatus.Done,
                                    message = body.message ?: "Subida"
                                )
                            }
                            okCount++
                        } else if (resp.code() == 409) {
                            val msg = body?.message ?: "Ya existe en el catálogo"
                            update(item.id) {
                                it.copy(status = UploadStatus.Done, message = msg)
                            }
                            okCount++
                        } else {
                            update(item.id) {
                                it.copy(
                                    status = UploadStatus.Error,
                                    message = "HTTP ${resp.code()}"
                                )
                            }
                            errCount++
                        }
                    } catch (ce: CancellationException) {
                        // Solo si de verdad nos cancelan: dejamos el item
                        // en Pending para que se pueda reintentar.
                        update(item.id) {
                            it.copy(status = UploadStatus.Pending, message = "Cancelada")
                        }
                        throw ce
                    } catch (t: Throwable) {
                        update(item.id) {
                            it.copy(
                                status = UploadStatus.Error,
                                message = t.message ?: "Error al subir"
                            )
                        }
                        errCount++
                    }
                }
                _events.tryEmit(
                    if (errCount == 0) "Subidas $okCount canciones"
                    else "$okCount OK · $errCount con error"
                )
            } finally {
                _uploading.value = false
                uploadJob = null
            }
        }
    }

    private fun update(id: Long, transform: (UploadItem) -> UploadItem) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
    }

    private fun buildMultipart(item: UploadItem): MultipartBody.Part {
        val mediaType = "audio/*".toMediaTypeOrNull()
        val resolver = context.contentResolver
        val mime = resolver.getType(item.uri) ?: "audio/*"
        val body = object : RequestBody() {
            override fun contentType() = mime.toMediaTypeOrNull() ?: mediaType
            override fun contentLength(): Long =
                if (item.sizeBytes > 0) item.sizeBytes else -1L
            override fun writeTo(sink: okio.BufferedSink) {
                resolver.openInputStream(item.uri)?.use { input ->
                    input.source().buffer().use { src -> sink.writeAll(src) }
                } ?: throw java.io.IOException("No se pudo abrir ${item.displayName}")
            }
        }
        return MultipartBody.Part.createFormData(
            name = "file",
            filename = item.displayName,
            body = body
        )
    }
}

data class PickedFile(val uri: Uri, val displayName: String, val sizeBytes: Long)
