package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Piloto de migración a kotlinx.serialization. Un DTO con @Serializable
 * entra automáticamente por el converter kotlinx (configurado primero en
 * [com.musarum.mobile.di.NetworkContainer.retrofit]); el resto sigue
 * decodificándose con Gson.
 *
 * Para migrar otro DTO: añadir {@code @Serializable} a la data class y, si
 * el nombre del campo JSON difiere del de Kotlin, {@code @SerialName("...")}
 * sobre cada propiedad desalineada. Lista en CLAUDE.md (bloque 51).
 */
@Serializable
data class TokenResponse(
    val token: String
)
