package com.musarum.mobile.data.network.dto

/**
 * Respuesta del backend al subir un fichero con POST /admin/songs/upload.
 * `imported` indica si el scanner consiguió insertar la fila en caliente
 * (true) o se queda en _inbox para el próximo escaneo (false). `songId`
 * puede llegar null si imported=false o si no se pudo recuperar tras el
 * import; la UI muestra el estado de todas formas con `fileName` y
 * `message`.
 */
data class UploadSongResultDto(
    val songId: Long?,
    val fileName: String?,
    val message: String?,
    val imported: Boolean = false
)
