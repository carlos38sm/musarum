package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

@Serializable

data class BackupFileDto(
    val fileName: String,
    val sizeBytes: Long,
    val modifiedAt: String?
)
