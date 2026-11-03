package com.musarum.mobile.data.user

import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.ChangePasswordRequest
import com.musarum.mobile.data.network.dto.CreateUserRequest
import com.musarum.mobile.data.network.dto.ScannerStatusDto
import com.musarum.mobile.data.network.dto.UpdateUserRequest
import com.musarum.mobile.data.network.dto.UserResponseDto
import com.musarum.mobile.data.network.dto.UserStatsDto
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Repositorio del perfil autenticado. Cachea el último /me recibido para que
 * la pantalla de Perfil renderice de inmediato cuando llega la respuesta.
 */
class UserRepository(private val apiService: ApiService) {

    private val _me = MutableStateFlow<UserResponseDto?>(null)
    val me: StateFlow<UserResponseDto?> = _me.asStateFlow()

    suspend fun refresh(): UserResponseDto {
        val user = apiService.getMe()
        _me.value = user
        return user
    }

    suspend fun updateNick(nick: String): UserResponseDto {
        val updated = apiService.updateMe(UpdateUserRequest(nick = nick))
        _me.value = updated
        return updated
    }

    suspend fun changePassword(current: String, new: String) {
        apiService.changePassword(ChangePasswordRequest(current, new))
    }

    suspend fun stats(): UserStatsDto = apiService.getMyStats()

    /**
     * Crea un usuario nuevo. Solo el OWNER tiene permisos; si lo intenta
     * un MEMBER el backend devuelve 403 y Retrofit lanza HttpException.
     * role=null → el backend asignará MEMBER por defecto.
     */
    suspend fun createUser(nick: String, password: String, role: String? = null): UserResponseDto =
        apiService.createUser(CreateUserRequest(nick = nick, password = password, role = role))

    suspend fun triggerScan() {
        val response = apiService.triggerLibraryScan()
        if (!response.isSuccessful) {
            error("Scan rechazado (HTTP ${response.code()})")
        }
    }

    suspend fun scannerStatus(): ScannerStatusDto = apiService.scannerStatus()

    // ── Avatar ──
    suspend fun uploadAvatar(bytes: ByteArray, mime: String): UserResponseDto {
        val body = okhttp3.RequestBody.create(mime.toMediaTypeOrNull(), bytes)
        val part = okhttp3.MultipartBody.Part.createFormData(
            name = "file",
            filename = "avatar.${mime.substringAfter('/').ifBlank { "jpg" }}",
            body = body
        )
        val updated = apiService.uploadMyAvatar(part)
        _me.value = updated
        return updated
    }

    suspend fun deleteAvatar(): UserResponseDto {
        val updated = apiService.deleteMyAvatar()
        _me.value = updated
        return updated
    }

    // ── Admin de usuarios (solo OWNER) ──
    suspend fun listAll(): List<UserResponseDto> = apiService.listAllUsers()

    suspend fun setBlocked(id: Long, blocked: Boolean): UserResponseDto =
        apiService.setUserBlocked(id, blocked)

    suspend fun deleteUser(id: Long) {
        apiService.deleteUser(id)
    }

    fun clear() {
        _me.value = null
    }
}
