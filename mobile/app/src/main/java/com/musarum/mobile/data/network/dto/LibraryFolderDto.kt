package com.musarum.mobile.data.network.dto

/**
 * Sub-carpeta de biblioteca registrada en el servidor. `existsOnDisk=false`
 * cuando el OWNER la añadió pero el directorio físico ya no existe (p. ej.
 * renombrado en el host sin avisar).
 */
data class LibraryFolderDto(
    val id: Long,
    val path: String,
    val name: String,
    val active: Boolean,
    val existsOnDisk: Boolean,
    val songCount: Int = 0
)

/** Sub-carpeta encontrada bajo el paraguas que aún no está registrada. */
data class DiscoveredFolderDto(
    val path: String,
    val name: String
)

data class AddFolderRequest(val path: String)
