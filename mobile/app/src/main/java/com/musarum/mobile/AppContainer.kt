package com.musarum.mobile

import android.content.Context
import com.musarum.mobile.data.BaseUrlStore
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

    /**
     * audioSessionId del ExoPlayer del PlayerService. Lo publica el service
     * cuando el player cambia de sessionId (incluida la primera vez). El
     * visualizador FFT de la NowPlayingScreen lo lee para enganchar su
     * {@code android.media.audiofx.Visualizer}.
     */
    val audioSessionIdFlow: kotlinx.coroutines.flow.MutableStateFlow<Int> =
        kotlinx.coroutines.flow.MutableStateFlow(0)

    /**
     * Dispositivo de salida preferido (id del AudioDeviceInfo.getId()) o null
     * para dejar que el sistema decida. El PlayerService observa este flow y
     * llama ExoPlayer.setPreferredAudioDevice al dispositivo que lo tenga.
     * El BottomSheet de salida en NowPlayingScreen lo escribe al seleccionar.
     */
    val preferredAudioDeviceId: kotlinx.coroutines.flow.MutableStateFlow<Int?> =
        kotlinx.coroutines.flow.MutableStateFlow(null)

    val baseUrlStore: BaseUrlStore = BaseUrlStore(context)
    /** URL del backend resuelta UNA vez al construir AppContainer. Todos
     *  los callers (CoverImage, DownloadsManager, PlayerController, …) leen
     *  de aquí en vez de BuildConfig para respetar el override del user.
     *  Si el override guardado no es una URL válida (user pegó basura en el
     *  ServerUrlDialog), lo borramos y caemos al baked-in. Sin este fallback
     *  la app entraría en crash-loop porque Retrofit.Builder.baseUrl lanza
     *  al construir y no hay forma de que el user vuelva a abrir el diálogo. */
    val baseUrl: String = run {
        val candidate = baseUrlStore.current()
        if (BaseUrlStore.isValid(candidate)) candidate
        else {
            baseUrlStore.clear()
            baseUrlStore.current()
        }
    }

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
        // 120 s de read: /admin/server/stats recorre _lyrics/_covers en disco
        // y clasifica cada .lrc. Con biblioteca grande + scanner corriendo en
        // paralelo puede superar los 60 s. Subimos para no cortar en seco.
        .readTimeout(120, TimeUnit.SECONDS)
        // Connection pool más agresivo: el Home dispara 7 llamadas paralelas,
        // y el default (5 conexiones, keep-alive 5 min) hacía que las dos
        // extras esperaran a que las primeras liberaran. 16 conexiones +
        // 10 min de keep-alive da margen sin saturar el backend (Tomcat
        // acepta 200 por defecto, y Tailscale son solo 4 users).
        .connectionPool(okhttp3.ConnectionPool(16, 10, TimeUnit.MINUTES))
        // Cache HTTP de 100 MB en disco para respuestas con Cache-Control.
        // Covers de álbum/artista/avatar/placeholders llegan con
        // "Cache-Control: public, max-age=604800" desde el backend → la
        // segunda vez que Coil o cualquier API GET pide la misma URL, OkHttp
        // responde desde disco sin ir a la red. Esto hace que volver al
        // Home, Albums, Artists, etc. sean instantáneos la segunda carga.
        .cache(okhttp3.Cache(
            directory = java.io.File(context.cacheDir, "okhttp_cache"),
            maxSize = 100L * 1024 * 1024
        ))
        .build()

    private val retrofit: Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
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
        applicationScope = applicationScope,
        baseUrl = baseUrl
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
        downloadsRepository = downloadsRepository,
        baseUrl = baseUrl
    )
}
