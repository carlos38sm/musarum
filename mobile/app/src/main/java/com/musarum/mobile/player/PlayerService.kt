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
    // Elevado a campo para que applyPreferredAudioDevice pueda tocarlo.
    private var exoPlayer: ExoPlayer? = null
    private val serviceScope: CoroutineScope = MainScope()
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null

    private fun acquireWifiLock() {
        if (wifiLock == null) {
            val wifi = applicationContext.getSystemService(android.content.Context.WIFI_SERVICE)
                    as? android.net.wifi.WifiManager ?: return
            wifiLock = wifi.createWifiLock(
                android.net.wifi.WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                "musarum:wifi"
            ).apply { setReferenceCounted(false) }
        }
        runCatching { if (wifiLock?.isHeld == false) wifiLock?.acquire() }
    }

    private fun releaseWifiLock() {
        runCatching { if (wifiLock?.isHeld == true) wifiLock?.release() }
    }

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

        val player: ExoPlayer = ExoPlayer.Builder(this)
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

        exoPlayer = player
        mediaSession = MediaSession.Builder(this, player).build()

        // Ecualizador 5 bandas enganchado al audio session del ExoPlayer.
        // Esperamos a que el player tenga sessionId (puede ser 0 hasta que
        // prepare la primera pista) con un listener de eventos; cuando sea
        // válido, instanciamos el Equalizer y nos suscribimos a cambios de
        // preferencias para re-aplicar bandas en caliente.
        val container = app.container
        // Publica el sessionId INICIAL de inmediato: en algunos dispositivos
        // ExoPlayer lo asigna al construir sin volver a disparar
        // onAudioSessionIdChanged, por lo que el visualizador se quedaba a 0
        // para siempre si confiabas solo en el callback. Ahora leemos el id
        // ya al arrancar y lo publicamos si no es UNSET.
        val initialSessionId = player.audioSessionId
        if (initialSessionId != C.AUDIO_SESSION_ID_UNSET) {
            container.audioSessionIdFlow.value = initialSessionId
            rebindEqualizer(initialSessionId)
        }
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                if (audioSessionId == C.AUDIO_SESSION_ID_UNSET) return
                // Si ya hay un Equalizer apuntando a OTRO sessionId (Android
                // puede renovar el id al cambiar de pipeline de audio o tras
                // pausas largas con la pantalla bloqueada), lo liberamos y
                // creamos uno nuevo. Antes se instanciaba una sola vez y
                // dejaba de aplicar las bandas al viejo id → EQ se "apagaba"
                // sin motivo aparente.
                rebindEqualizer(audioSessionId)
                // Publicamos el sessionId para que el visualizador FFT de la
                // UI (NowPlayingScreen) pueda engancharse. Distinto de 0 y
                // de UNSET.
                container.audioSessionIdFlow.value = audioSessionId
            }

            // WifiLock HIGH_PERF: lo agarramos mientras isPlaying=true y lo
            // soltamos al pausar o parar. Esto resuelve el bug recurrente de
            // "se corta el Bluetooth" o "se para la canción al bloquear" en
            // algunos Android (Xiaomi/Samsung sobre todo): sin HIGH_PERF, el
            // chip Wi-Fi baja a modo ahorro con pantalla apagada, el stream
            // se retrasa, el buffer de ExoPlayer se vacía, y el handshake
            // con el BT headset se cae.
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying) acquireWifiLock() else releaseWifiLock()
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

        // Observa el dispositivo de salida preferido. Cuando el BottomSheet
        // de NowPlayingScreen elige uno, ExoPlayer enruta la pista hacia él.
        // null = "deja decidir al sistema" (comportamiento original).
        serviceScope.launch {
            container.preferredAudioDeviceId.collect { deviceId ->
                runCatching { applyPreferredAudioDevice(deviceId) }
            }
        }
    }

    /**
     * Resuelve el AudioDeviceInfo por id en AudioManager y se lo aplica al
     * ExoPlayer. Si el id es null o el dispositivo ya no está disponible
     * (lo desconectaron), pasa null → el sistema vuelve a decidir.
     */
    private fun applyPreferredAudioDevice(deviceId: Int?) {
        val target: android.media.AudioDeviceInfo? = if (deviceId == null) null else {
            val am = getSystemService(AUDIO_SERVICE) as android.media.AudioManager
            am.getDevices(android.media.AudioManager.GET_DEVICES_OUTPUTS)
                .firstOrNull { it.id == deviceId }
        }
        runCatching { exoPlayer?.setPreferredAudioDevice(target) }
    }

    private var equalizerSessionId: Int = 0

    private fun rebindEqualizer(audioSessionId: Int) {
        if (equalizer != null && equalizerSessionId == audioSessionId) return
        // Libera el viejo si apuntaba a otro sessionId.
        runCatching { equalizer?.release() }
        equalizer = null
        try {
            val eq = Equalizer(0, audioSessionId)
            val prefs = (application as MusarumApplication).container.playerPreferences
            eq.enabled = prefs.equalizerEnabled.value
            if (eq.enabled) applyBands(eq, prefs.equalizerBands.value)
            equalizer = eq
            equalizerSessionId = audioSessionId
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
        releaseWifiLock()
        wifiLock = null
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
