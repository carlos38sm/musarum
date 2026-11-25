package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

@Serializable

data class LoginRequest(
    val nick: String,
    val password: String
)