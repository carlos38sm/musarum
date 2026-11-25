package com.musarum.mobile.di

import android.content.Context
import com.musarum.mobile.player.PlayerController
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Agrupa reproductor + flows compartidos con el PlayerService (sessionId
 * y dispositivo de salida preferido).
 *
 * Mantener esto apartado del DataContainer deja la puerta abierta a que en
 * el futuro el player se inicialice SOLO tras login (hoy se crea con la
 * app). También facilita tests de UI que no necesitan Media3 — un fake de
 * esta clase basta.
 */
class PlayerContainer(
    context: Context,
    network: NetworkContainer,
    data: DataContainer
) {
    /** audioSessionId del ExoPlayer del PlayerService. Lo publica el service
     *  cuando el player cambia de sessionId. El visualizador FFT lo lee. */
    val audioSessionIdFlow: MutableStateFlow<Int> = MutableStateFlow(0)

    /** Dispositivo de salida preferido (id del AudioDeviceInfo.getId()) o
     *  null para dejar que el sistema decida. */
    val preferredAudioDeviceId: MutableStateFlow<Int?> = MutableStateFlow(null)

    val playerController: PlayerController = PlayerController(
        context = context,
        songRepository = data.songRepository,
        playHistoryRepository = data.playHistoryRepository,
        playerPreferences = data.playerPreferences,
        okHttpClient = network.okHttpClient,
        downloadsRepository = data.downloadsRepository,
        baseUrl = network.baseUrl
    )
}
