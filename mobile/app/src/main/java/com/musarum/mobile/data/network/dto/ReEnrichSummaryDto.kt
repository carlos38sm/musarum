package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Respuesta de POST /admin/server/re-enrich-all. Devuelve cuÃ¡ntas canciones se
 * encolaron para re-enriquecer vs el total del catÃ¡logo. El trabajo va
 * fire-and-forget: los datos nuevos (bio, BPM, tonalidad, replaygain) aparecen
 * conforme Emendatium responde a cada una.
 */
@Serializable
data class ReEnrichSummaryDto(
    val queued: Int,
    val total: Long
)
