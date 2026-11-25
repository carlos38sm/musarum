package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Snapshot del progreso de "Re-enriquecer todo". El móvil hace polling cada
 * 2s mientras running=true. Cuando el backend lo marca false y
 * finishedAtMs != null, el polling para y se muestra el resumen final.
 */
@Serializable
data class EnrichProgressDto(
    val running: Boolean,
    val total: Long,
    val processed: Long,
    val succeeded: Long,
    val failed: Long,
    val startedAtMs: Long?,
    val finishedAtMs: Long?
)
