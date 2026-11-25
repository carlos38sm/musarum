package com.musarum.mobile.data.network.dto

import kotlinx.serialization.Serializable

/** Nueva posiciÃ³n 0-based de un entry dentro de la playlist. */
@Serializable
data class MoveEntryRequest(val position: Int)
