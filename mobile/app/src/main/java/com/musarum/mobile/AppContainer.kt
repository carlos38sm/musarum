package com.musarum.mobile

import android.content.Context
import com.musarum.mobile.data.TokenManager
import com.musarum.mobile.data.auth.AuthRepository
import com.musarum.mobile.data.downloads.DownloadsManager
import com.musarum.mobile.data.downloads.DownloadsRepository
import com.musarum.mobile.data.library.LibraryFolderRepository
import com.musarum.mobile.data.music.AlbumRepository
import com.musarum.mobile.data.music.ArtistRepository
import com.musarum.mobile.data.music.FavoriteRepository
import com.musarum.mobile.data.music.PlayHistoryRepository
import com.musarum.mobile.data.music.PlaylistRepository
import com.musarum.mobile.data.music.SearchRepository
import com.musarum.mobile.data.music.SongRepository
import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.preferences.PlayerPreferences
import com.musarum.mobile.data.user.UserRepository
import com.musarum.mobile.data.network.AuthInterceptor
import com.musarum.mobile.data.network.UnauthorizedInterceptor
import com.musarum.mobile.player.PlayerController
import com.musarum.mobile.session.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.converter.scalars.ScalarsConverterFactory
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {

    val applicationScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val tokenManager: TokenManager = TokenManager(context, applicationScope)

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = if (BuildConfig.DEBUG) HttpLoggingInterceptor.Level.BODY
        else HttpLoggingInterceptor.Level.NONE
    }

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .addInterceptor(AuthInterceptor(tokenManager))
        .addInterceptor(UnauthorizedInterceptor(tokenManager, applicationScope))
        .addInterceptor(loggingInterceptor)
        .connectTimeout(15, TimeUnit.SECONDS)
        // 60 s de read: el endpoint /admin/server/stats recorre _lyrics/_covers
        // en disco y puede tardar varios segundos con bibliotecas medias.
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(BuildConfig.MUSARUM_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(ScalarsConverterFactory.create())
        .addConverterFactory(GsonConverterFactory.create())
        .build()

    val apiService: ApiService = retrofit.create(ApiService::class.java)

    val authRepository: AuthRepository =
        AuthRepository(apiService, tokenManager, applicationScope)

    val songRepository: SongRepository = SongRepository(apiService)
    val albumRepository: AlbumRepository = AlbumRepository(apiService)
    val artistRepository: ArtistRepository = ArtistRepository(apiService)
    val searchRepository: SearchRepository = SearchRepository(apiService)
    val favoriteRepository: FavoriteRepository = FavoriteRepository(apiService)
    val playlistRepository: PlaylistRepository = PlaylistRepository(apiService)
    val playHistoryRepository: PlayHistoryRepository = PlayHistoryRepository(apiService)
    val userRepository: UserRepository = UserRepository(apiService)
    val libraryFolderRepository: LibraryFolderRepository = LibraryFolderRepository(apiService)
    val playerPreferences: PlayerPreferences = PlayerPreferences(context)

    val downloadsRepository: DownloadsRepository = DownloadsRepository(context)
    val downloadsManager: DownloadsManager = DownloadsManager(
        repository = downloadsRepository,
        okHttpClient = okHttpClient,
        preferences = playerPreferences,
        applicationScope = applicationScope
    ).also {
        // Al arrancar, barre las caducadas. El currentPlayingId es null porque
        // el controller aún no ha arrancado reproducción alguna.
        it.cleanExpired(currentlyPlayingId = null)
    }

    val updateChecker: UpdateChecker = UpdateChecker(apiService, applicationScope).also {
        it.checkOnce()
    }

    val playerController: PlayerController = PlayerController(
        context = context,
        songRepository = songRepository,
        playHistoryRepository = playHistoryRepository,
        playerPreferences = playerPreferences,
        okHttpClient = okHttpClient,
        downloadsRepository = downloadsRepository
    )
}
