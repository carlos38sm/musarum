package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

@Serializable

data class UserResponseDto(
    val id: Long,
    val nick: String,
    // Nullable porque /users/search (pÃºblico entre users) devuelve null
    // para no revelar quiÃ©n es OWNER. /users/me y /users/admin sÃ­ lo traen.
    val userRole: String? = null,
    val createdAt: String? = null,
    val avatarUrl: String? = null,
    val blocked: Boolean = false,
    // Toggles de privacidad (Home â€” convivencia). Nullable: /users/search
    // los deja fuera para no filtrar preferencias ajenas.
    val shareNowPlaying: Boolean? = null,
    val shareTaste: Boolean? = null,
    // Ãšltima conexiÃ³n (login). Solo se rellena en /users/admin para el
    // OWNER; en el resto de respuestas viene null. ISO-8601 UTC.
    val lastLoginAt: String? = null
)

@Serializable

data class UpdatePrivacyRequest(
    val shareNowPlaying: Boolean? = null,
    val shareTaste: Boolean? = null
)

@Serializable

data class UpdateUserRequest(val nick: String)

@Serializable

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

/**
 * Alta de usuario â€” solo el OWNER puede llamarlo. role=null â†’ MEMBER por
 * defecto en el backend. Se envÃ­a como String ("OWNER" / "MEMBER") para
 * que casar con el enum de Java sin configuraciones extra en Gson.
 */
@Serializable
data class CreateUserRequest(
    val nick: String,
    val password: String,
    val role: String? = null
)

/**
 * Snapshot del escaneo en curso. `running=true` â†’ polling vivo; cuando
 * cambia a false, lastResult trae el desglose final (importadas,
 * duplicadas en disco, etc.).
 */
@Serializable
data class ScannerStatusDto(
    val running: Boolean,
    val total: Int,
    val processed: Int,
    val currentFile: String? = null,
    val startedAt: Long? = null,
    val lastResult: ImportResultDto? = null,
    // "hashing" (fase 1: lectura + SHA-256), "importing" (fase 2: DB), null idle.
    val phase: String? = null
)

@Serializable

data class ImportResultDto(
    val totalOnDisk: Int,
    val imported: Int,
    val failed: Int,
    val deleted: Int,
    val duplicates: Int,
    val relocated: Int,
    val hashErrors: Int
)

@Serializable

data class UserStatsDto(
    val totalPlays: Long = 0,
    val uniqueSongsPlayed: Long = 0,
    val totalListenedMs: Long = 0,
    val topSongTitle: String? = null,
    val topArtistName: String? = null,
    val topGenreName: String? = null
)
