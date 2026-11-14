package com.musarum.mobile.player

import android.content.Intent
import android.media.audiofx.Equalizer
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.musarum.mobile.MusarumApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@OptIn(UnstableApi::class)
class PlayerService : MediaSessionService() {

    private var mediaSession: MediaSession? = null
    private var equalizer: Equalizer? = null
    private var equalizerJob: Job? = null
    private val serviceScope: CoroutineScope = MainScope()

    override fun onCreate() {
        super.onCreate()
        val app = application as MusarumApplication
        val playerPreferences = app.container.playerPreferences

        // Reutilizamos el OkHttpClient del AppContainer. Lleva ya AuthInterceptor
        // (JWT) y UnauthorizedInterceptor (si el token expira durante streaming,
        // el usuario vuelve al login en vez de quedarse con 401 en bucle).
        // Antes montábamos un cliente aparte aquí → connection pool duplicado
        // y sin UnauthorizedInterceptor.
        val okHttpClient = app.container.okHttpClient

        // OkHttpDataSource solo entiende HTTP(S). Para que las descargas
        // offline (uri file://) funcionen envolvemos con DefaultDataSource,
        // que delega en OkHttp para HTTP y usa el reader nativo para file://,
        // content:// y asset://.
        val httpDataSourceFactory = OkHttpDataSource.Factory(okHttpClient)
        val dataSourceFactory = DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(dataSourceFactory)

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(C.USAGE_MEDIA)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            // Con la pantalla bloqueada el móvil deja al CPU dormir salvo que
            // alguien pida wake lock. Sin esto, al llegar al final del buffer
            // el player entra en STATE_BUFFERING, no puede pedir más bytes y
            // se "come" segundos → entre canciones Bluetooth hace handshake.
            // NETWORK cubre streaming y descargas offline sin cambiar nada.
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        mediaSession = MediaSession.Builder(this, player).build()

        // Ecualizador 5 bandas enganchado al audio session del ExoPlayer.
        // Esperamos a que el player tenga sessionId (puede ser 0 hasta que
        // prepare la primera pista) con un listener de eventos; cuando sea
        // válido, instanciamos el Equalizer y nos suscribimos a cambios de
        // preferencias para re-aplicar bandas en caliente.
        val container = app.container
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
                initEqualizer(audioSessionId)
                // Publicamos el sessionId para que el visualizador FFT de la
                // UI (NowPlayingScreen) pueda engancharse. Distinto de 0 y
                // de UNSET.
                container.audioSessionIdFlow.value = audioSessionId
            }
        })

        equalizerJob = serviceScope.launch {
            combine(
                playerPreferences.equalizerEnabled,
                playerPreferences.equalizerBands
            ) { enabled, bands -> enabled to bands }
                .distinctUntilChanged()
                .collect { (enabled, bands) ->
                    equalizer?.let { eq ->
                        runCatching {
                            eq.enabled = enabled
                            if (enabled) applyBands(eq, bands)
                        }
                    }
                }
        }
    }

    private fun initEqualizer(audioSessionId: Int) {
        if (equalizer != null) return
        try {
            val eq = Equalizer(0, audioSessionId)
            val prefs = (application as MusarumApplication).container.playerPreferences
            eq.enabled = prefs.equalizerEnabled.value
            if (eq.enabled) applyBands(eq, prefs.equalizerBands.value)
            equalizer = eq
        } catch (t: Throwable) {
            // En algunas ROMs limpias AudioEffect falla silenciosamente.
            // No tiramos la app: la UI mostrará que no se aplica.
            Log.w("PlayerService", "No pude instanciar Equalizer: ${t.message}")
        }
    }

    private fun applyBands(eq: Equalizer, bands: List<Int>) {
        val numBands = eq.numberOfBands.toInt()
        for (i in 0 until minOf(numBands, bands.size)) {
            runCatching {
                eq.setBandLevel(i.toShort(), bands[i].toShort())
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        // El usuario mató la app desde recents. Antes solo parábamos si no
        // había música sonando, pero esto confundía: la expectativa es que
        // "quitar la tarjeta de la app" apague Musarum (como Spotify
        // Freemium o YouTube Music gratis). Paramos SIEMPRE: libera el
        // player, mata el servicio y la notificación desaparece.
        mediaSession?.player?.run {
            stop()
            clearMediaItems()
        }
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { equalizer?.release() }
        equalizer = null
        equalizerJob?.cancel()
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
