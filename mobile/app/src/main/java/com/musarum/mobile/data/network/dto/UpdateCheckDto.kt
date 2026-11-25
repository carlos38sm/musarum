package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Respuesta de POST /admin/server/check-updates. Dos bloques (APK y backend)
 * independientes + lista de errores no fatales. El backend NUNCA lanza
 * excepción si una parte falla: la otra sigue y el fallo se reporta aquí.
 */
@Serializable
data class UpdateCheckDto(
    val apk: ApkCheckDto?,
    val backend: BackendCheckDto?,
    val errors: List<String> = emptyList()
)

/**
 * - latestTag: p. ej. "1.0.60" (ya sin el prefijo android-v).
 * - previouslyAnnounced: lo que la DB tenía antes.
 * - published: true si acabamos de publicar el anuncio ahora.
 */
@Serializable
data class ApkCheckDto(
    val latestTag: String?,
    val previouslyAnnounced: String?,
    val published: Boolean
)

/**
 * - latestTag: p. ej. "1.0.44".
 * - watchtowerTriggered: true si Watchtower respondió 2xx al update manual.
 *   Importante: Watchtower pulla y recrea SOLO si hay imagen nueva; un
 *   triggered=true con la misma imagen es no-op.
 */
@Serializable
data class BackendCheckDto(
    val latestTag: String?,
    val watchtowerTriggered: Boolean
)
