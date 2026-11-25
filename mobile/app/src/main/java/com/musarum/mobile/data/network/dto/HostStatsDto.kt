package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/**
 * Snapshot de métricas del SO del servidor (CPU, RAM, swap, red, disco IO).
 * Lo pinta la tarjeta "Máquina" del panel "Análisis del servidor". Las
 * velocidades Rx/Tx y de IO vienen ya calculadas por el backend sobre
 * delta entre lecturas consecutivas: el móvil solo las formatea.
 */
@Serializable
data class HostStatsDto(
    val capturedAtMs: Long,
    val osName: String,
    val cpuModel: String,
    val cpuThreads: Int,
    // 0.0..1.0 — el UI lo multiplica por 100 para mostrar %.
    val cpuLoadSystem: Double,
    val cpuLoadProcess: Double,
    val memUsedBytes: Long,
    val memTotalBytes: Long,
    val swapUsedBytes: Long,
    val swapTotalBytes: Long,
    val osUptimeSeconds: Long,
    val jvmThreadCount: Int,
    val networkInterfaces: List<NetworkInterfaceDto> = emptyList(),
    val diskIo: List<DiskIoDto> = emptyList()
) {
    @Serializable
    data class NetworkInterfaceDto(
        val name: String,
        val displayName: String,
        val ipv4: String?,
        val ipv6: String?,
        val up: Boolean,
        val speedBitsPerSec: Long,
        val totalRxBytes: Long,
        val totalTxBytes: Long,
        val rxBytesPerSec: Long,
        val txBytesPerSec: Long
    )

    @Serializable

    data class DiskIoDto(
        val name: String,
        val model: String?,
        val sizeBytes: Long,
        val totalReadBytes: Long,
        val totalWriteBytes: Long,
        val readBytesPerSec: Long,
        val writeBytesPerSec: Long
    )
}
