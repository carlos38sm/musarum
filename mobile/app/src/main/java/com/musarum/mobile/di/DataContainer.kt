package com.musarum.mobile.di

import android.content.Context
import com.musarum.mobile.data.auth.AuthRepository
import com.musarum.mobile.data.downloads.DownloadsManager
import com.musarum.mobile.data.downloads.DownloadsRepository
import com.musarum.mobile.data.uploads.UploadsManager
import com.musarum.mobile.data.library.LibraryFolderRepository
import com.musarum.mobile.data.music.AlbumRepository
import com.musarum.mobile.data.music.ArtistRepository
import com.musarum.mobile.data.music.FavoriteRepository
import com.musarum.mobile.data.music.PlayHistoryRepository
import com.musarum.mobile.data.music.PlaylistRepository
import com.musarum.mobile.data.music.SearchRepository
import com.musarum.mobile.data.music.SongRepository
import com.musarum.mobile.data.preferences.PlayerPreferences
import com.musarum.mobile.data.user.UserRepository
import com.musarum.mobile.session.UpdateChecker
import kotlinx.coroutines.CoroutineScope

/**
 * Agrupa todos los repositorios de datos — la capa que las pantallas usan
 * directamente. Solo depende del [NetworkContainer] para construir cada
 * uno a partir de {@code apiService}.
 */
class DataContainer(
    context: Context,
    network: NetworkContainer,
    applicationScope: CoroutineScope
) {
    val authRepository: AuthRepository =
        AuthRepository(network.apiService, network.tokenManager, applicationScope)

    val songRepository: SongRepository = SongRepository(network.apiService)
    val albumRepository: AlbumRepository = AlbumRepository(network.apiService)
    val artistRepository: ArtistRepository = ArtistRepository(network.apiService)
    val searchRepository: SearchRepository = SearchRepository(network.apiService)
    val favoriteRepository: FavoriteRepository = FavoriteRepository(network.apiService)
    val playlistRepository: PlaylistRepository = PlaylistRepository(network.apiService)
    val playHistoryRepository: PlayHistoryRepository = PlayHistoryRepository(network.apiService)
    val userRepository: UserRepository = UserRepository(network.apiService)
    val libraryFolderRepository: LibraryFolderRepository = LibraryFolderRepository(network.apiService)
    val playerPreferences: PlayerPreferences = PlayerPreferences(context)

    val downloadsRepository: DownloadsRepository = DownloadsRepository(context)
    val downloadsManager: DownloadsManager = DownloadsManager(
        repository = downloadsRepository,
        okHttpClient = network.okHttpClient,
        preferences = playerPreferences,
        applicationScope = applicationScope,
        baseUrl = network.baseUrl
    ).also {
        it.cleanExpired(currentlyPlayingId = null)
    }

    val updateChecker: UpdateChecker = UpdateChecker(network.apiService, applicationScope).also {
        it.checkOnce()
    }

    /** Subidas de canciones en vivo. Vive aquí (no en el VM) para que al
     *  salir de UploadSongsScreen la lista y el job sigan vivos. */
    val uploadsManager: UploadsManager = UploadsManager(
        context = context,
        apiService = network.apiService,
        applicationScope = applicationScope
    )
}
