package com.musarum.mobile.data.network.dto

data class BackupFileDto(
    val fileName: String,
    val sizeBytes: Long,
    val modifiedAt: String?
)
