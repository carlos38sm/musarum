package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Historial agregado de los Ãºltimos 30 min del servidor (1 muestra cada
 * sampleIntervalMs, tÃ­picamente 10s). Lo pinta `MachineCard` como
 * sparklines encima de los contadores en vivo.
 *
 * El backend acumula el buffer en memoria y lo pierde al reiniciar: si
 * la respuesta trae menos muestras que capacity, significa que el backend
 * arrancÃ³ hace poco. El mÃ³vil pinta lo que haya.
 */
@Serializable
data class HostHistoryDto(
    val sampleIntervalMs: Long,
    val capacity: Int,
    val samples: List<Sample> = emptyList()
) {
    @Serializable
    data class Sample(
        val capturedAtMs: Long,
        // 0.0..1.0
        val cpuLoadSystem: Double,
        val memUsedBytes: Long,
        val memTotalBytes: Long,
        // Suma de todas las interfaces up
        val rxBytesPerSec: Long,
        val txBytesPerSec: Long,
        // Suma de todos los discos
        val readBytesPerSec: Long,
        val writeBytesPerSec: Long
    )
}
