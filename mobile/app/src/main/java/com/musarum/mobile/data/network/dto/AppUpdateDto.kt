package com.musarum.mobile.data.network.dto

/** Estado actual del anuncio de versión (lo que publica POST /admin/version). */
data class AppUpdateDto(
    val latestApk: String?,
    val apkUrl: String?,
    val mandatory: Boolean = false
)

/** Body de POST /admin/version — publica un nuevo anuncio de versión. */
data class PublishUpdateRequest(
    val latestApk: String?,
    val apkUrl: String?,
    val mandatory: Boolean
)
