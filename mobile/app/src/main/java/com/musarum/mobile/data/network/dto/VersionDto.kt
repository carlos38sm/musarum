package com.musarum.mobile.data.network.dto

/** Respuesta de GET /version. Todos los campos pueden ser null si el backend
 *  no tiene configurado el canal de releases todavía. */
data class VersionDto(
    val latestApk: String?,
    val apkUrl: String?,
    val mandatory: Boolean = false
)
