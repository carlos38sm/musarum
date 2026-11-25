package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/** Estado actual del anuncio de versiÃ³n (lo que publica POST /admin/version). */
@Serializable
data class AppUpdateDto(
    val latestApk: String?,
    val apkUrl: String?,
    val mandatory: Boolean = false
)

/** Body de POST /admin/version â€” publica un nuevo anuncio de versiÃ³n. */
@Serializable
data class PublishUpdateRequest(
    val latestApk: String?,
    val apkUrl: String?,
    val mandatory: Boolean
)
