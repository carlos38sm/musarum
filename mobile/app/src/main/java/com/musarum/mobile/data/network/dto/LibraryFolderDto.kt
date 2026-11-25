package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Sub-carpeta de biblioteca registrada en el servidor. `existsOnDisk=false`
 * cuando el OWNER la añadió pero el directorio físico ya no existe (p. ej.
 * renombrado en el host sin avisar).
 */
@Serializable
data class LibraryFolderDto(
    val id: Long,
    val path: String,
    val name: String,
    val active: Boolean,
    val existsOnDisk: Boolean,
    val songCount: Int = 0
)

/** Sub-carpeta encontrada bajo el paraguas que aún no está registrada. */
@Serializable
data class DiscoveredFolderDto(
    val path: String,
    val name: String
)

@Serializable

data class AddFolderRequest(val path: String)
