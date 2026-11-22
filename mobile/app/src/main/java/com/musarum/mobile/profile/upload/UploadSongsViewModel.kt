package com.musarum.mobile.profile.upload

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.AppContainer
import kotlinx.coroutines.CancellationException
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
    val id: Long,              // id estable para keys de LazyColumn
    val uri: Uri,
    val displayName: String,
    val sizeBytes: Long,
    val status: UploadStatus = UploadStatus.Pending,
    val message: String? = null
)

/**
 * ViewModel de la pantalla "Subir canciones". Mantiene una lista
 * mutable de PickedFile → UploadItem y los sube secuencialmente con
 * OkHttp Multipart. El estado de cada item se refleja vía copy +
 * emisión del flow, sin tocar el orden. Tras cada subida emite un
 * evento con el mensaje (snackbar).
 *
 * El upload lee el stream del ContentResolver dentro del RequestBody
 * — así no cargamos el fichero entero en memoria, OkHttp tira por
 * streaming al servidor. Emite error granular si falla cualquier
 * paso para no cortar el resto de la lista.
 */
class UploadSongsViewModel(
    private val context: Context,
    private val container: AppContainer
) : ViewModel() {

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

    fun startUpload() {
        if (_uploading.value) return
        viewModelScope.launch {
            _uploading.value = true
            try {
                // Iteramos sobre una copia por si llegan más files mientras
                // tanto — los nuevos se procesan en la próxima llamada.
                val snapshot = _items.value.filter { it.status == UploadStatus.Pending }
                var okCount = 0
                var errCount = 0
                for (item in snapshot) {
                    update(item.id) { it.copy(status = UploadStatus.Uploading, message = null) }
                    try {
                        val part = buildMultipart(item)
                        val resp = container.apiService.uploadSong(part)
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
                            // Duplicado en catálogo: lo marcamos como Done
                            // pero con nota aclaratoria. No es un error real.
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
            }
        }
    }

    private fun update(id: Long, transform: (UploadItem) -> UploadItem) {
        _items.value = _items.value.map { if (it.id == id) transform(it) else it }
    }

    /**
     * Construye el Part multipart "file" leyendo el URI en streaming. El
     * ContentResolver abre el InputStream bajo demanda cuando OkHttp pide
     * writeTo — no copiamos el fichero a memoria.
     */
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

    companion object {
        fun factory(
            context: Context,
            container: AppContainer
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    UploadSongsViewModel(context.applicationContext, container) as T
            }
    }
}

