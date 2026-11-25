package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/** Respuesta de GET /version. Todos los campos pueden ser null si el backend
 *  no tiene configurado el canal de releases todavía. */
@Serializable
data class VersionDto(
    val latestApk: String?,
    val apkUrl: String?,
    val mandatory: Boolean = false
)
