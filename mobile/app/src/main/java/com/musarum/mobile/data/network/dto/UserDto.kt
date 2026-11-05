package com.musarum.mobile.data.network.dto

data class UserResponseDto(
    val id: Long,
    val nick: String,
    val userRole: String,
    val createdAt: String? = null,
    val avatarUrl: String? = null,
    val blocked: Boolean = false
)

data class UpdateUserRequest(val nick: String)

data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String
)

/**
 * Alta de usuario — solo el OWNER puede llamarlo. role=null → MEMBER por
 * defecto en el backend. Se envía como String ("OWNER" / "MEMBER") para
 * que casar con el enum de Java sin configuraciones extra en Gson.
 */
data class CreateUserRequest(
    val nick: String,
    val password: String,
    val role: String? = null
)

/**
 * Snapshot del escaneo en curso. `running=true` → polling vivo; cuando
 * cambia a false, lastResult trae el desglose final (importadas,
 * duplicadas en disco, etc.).
 */
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

data class ImportResultDto(
    val totalOnDisk: Int,
    val imported: Int,
    val failed: Int,
    val deleted: Int,
    val duplicates: Int,
    val relocated: Int,
    val hashErrors: Int
)

data class UserStatsDto(
    val totalPlays: Long = 0,
    val uniqueSongsPlayed: Long = 0,
    val totalListenedMs: Long = 0,
    val topSongTitle: String? = null,
    val topArtistName: String? = null,
    val topGenreName: String? = null
)
