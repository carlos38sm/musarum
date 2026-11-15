package com.musarum.mobile.data.network.dto

/**
 * Perfil minimal del picker del login (bolitas estilo Netflix). El backend
 * SOLO envía id/nick/avatarUrl por esta ruta pública — ni role, ni blocked.
 */
data class LoginUserDto(
    val id: Long,
    val nick: String,
    val avatarUrl: String?
)
