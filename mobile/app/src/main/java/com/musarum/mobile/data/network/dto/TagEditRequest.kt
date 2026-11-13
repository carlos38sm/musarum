package com.musarum.mobile.data.network.dto

/**
 * Patch de tags ID3 + metadatos JPA. Campos nullable para enviar solo lo
 * que cambie. {@code renameFile=true} pide al backend intentar renombrar
 * el fichero del disco a "{artist} - {title}.{ext}".
 */
data class TagEditRequest(
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val genre: String? = null,
    val year: Int? = null,
    val trackNumber: Int? = null,
    val renameFile: Boolean? = null
)
