package com.musarum.mobile

import android.content.Context
import com.musarum.mobile.di.DataContainer
import com.musarum.mobile.di.NetworkContainer
import com.musarum.mobile.di.PlayerContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Fachada única que compone los tres sub-containers temáticos
 * ({@link NetworkContainer}, {@link DataContainer}, {@link PlayerContainer})
 * y expone su API de forma plana. Las pantallas siguen leyendo
 * {@code container.songRepository}, {@code container.playerController} etc.
 * — la separación por responsabilidad se ve solo si abres este fichero.
 *
 * Motivación del split:
 *  - NetworkContainer se puede instanciar solo en tests que solo pegan HTTP.
 *  - DataContainer no conoce Media3 → tests de ViewModels sin ExoPlayer.
 *  - PlayerContainer queda listo para construcción diferida (post-login) si
 *    algún día se quiere rehuir del coste de Media3 antes de autenticar.
 */
class AppContainer(context: Context) {

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val network: NetworkContainer = NetworkContainer(context, applicationScope)
    val data: DataContainer = DataContainer(context, network, applicationScope)
    val player: PlayerContainer = PlayerContainer(context, network, data)

    // ── Accesors "flat" para no romper call-sites existentes ───────────────
    // Mantener este alias deja intactas las 150+ referencias a container.X
    // repartidas por los Composables y ViewModels.

    val baseUrlStore get() = network.baseUrlStore
    val baseUrl get() = network.baseUrl
    val tokenManager get() = network.tokenManager
    val okHttpClient get() = network.okHttpClient
    val apiService get() = network.apiService

    val authRepository get() = data.authRepository
    val songRepository get() = data.songRepository
    val albumRepository get() = data.albumRepository
    val artistRepository get() = data.artistRepository
    val searchRepository get() = data.searchRepository
    val favoriteRepository get() = data.favoriteRepository
    val playlistRepository get() = data.playlistRepository
    val playHistoryRepository get() = data.playHistoryRepository
    val userRepository get() = data.userRepository
    val libraryFolderRepository get() = data.libraryFolderRepository
    val playerPreferences get() = data.playerPreferences
    val downloadsRepository get() = data.downloadsRepository
    val downloadsManager get() = data.downloadsManager
    val updateChecker get() = data.updateChecker
    val uploadsManager get() = data.uploadsManager

    val audioSessionIdFlow get() = player.audioSessionIdFlow
    val preferredAudioDeviceId get() = player.preferredAudioDeviceId
    val playerController get() = player.playerController
}
