package com.musarum.mobile.player

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.musarum.mobile.data.downloads.DownloadsRepository
import com.musarum.mobile.data.music.PlayHistoryRepository
import com.musarum.mobile.data.music.SongRepository
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.data.preferences.PlayerPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

enum class RepeatMode { OFF, ALL, ONE }

data class PlayerUiState(
    val currentSong: SongSummaryDto? = null,
    val currentCoverUrl: String? = null,
    val queue: List<SongSummaryDto> = emptyList(),
    val currentIndex: Int = -1,
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val shuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    // Sleep timer: timestamp absoluto en ms al que el player pausará solo.
    // null → sin timer activo. "Al final de la canción actual" se expresa
    // guardando el fin estimado de la pista; se recalcula si cambia duración.
    val sleepTimerAt: Long? = null
) {
    val hasPrevious: Boolean get() = currentIndex > 0
    val hasNext: Boolean get() =
        currentIndex in 0 until queue.size - 1 ||
        // Con "repetir lista" activo y al menos una canción, "siguiente" vuelve al
        // principio — el botón NO debe quedar apagado en la última.
        (repeatMode == RepeatMode.ALL && queue.isNotEmpty() && currentIndex >= 0)
}

class PlayerController(
    context: Context,
    private val songRepository: SongRepository,
    private val playHistoryRepository: PlayHistoryRepository,
    private val playerPreferences: PlayerPreferences,
    private val okHttpClient: OkHttpClient,
    private val downloadsRepository: DownloadsRepository,
    /** URL del backend resuelta por AppContainer. */
    private val baseUrl: String
) {

    private val appContext = context.applicationContext

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private var mediaController: MediaController? = null
    // Varias acciones pueden encolarse mientras conectamos (p. ej. el usuario
    // toca play/pause ANTES de que el futuro de buildAsync() termine). Antes
    // guardábamos solo la última y se perdían las anteriores — ahora las
    // encolamos todas y se disparan en orden al conectar.
    private val pendingActions: ArrayDeque<(MediaController) -> Unit> = ArrayDeque()
    // Flag para evitar lanzar DOS buildAsync() en paralelo mientras el
    // primero está pendiente — en ese caso mediaController seguía siendo
    // null y la guardia `if (mediaController != null) return` no filtraba.
    private var connecting: Boolean = false
    private var progressJob: Job? = null
    private var coverJob: Job? = null

    // Umbral de "reproducción válida" para el historial: 30s (tipo Spotify).
    // Al cambiar de canción reseteamos; al pasar los 30s hacemos un POST
    // best-effort al backend (/me/plays).
    private var loggedPlaySongId: Long? = null
    private val playThresholdMs = 30_000L

    // Fade de crossfade: una sola corrutina activa, bandera para evitar
    // relanzarlo durante los últimos segundos de cada track.
    private var fadeJob: Job? = null
    @Volatile private var fadingOut: Boolean = false

    // Crossfade REAL: ExoPlayer auxiliar que suena solapado con el principal
    // durante los últimos N segundos. Al terminar el ramp, hacemos handoff al
    // principal con seekTo(nextIndex, auxPos) y liberamos el auxiliar.
    private var auxPlayer: ExoPlayer? = null
    @Volatile private var crossfadeInProgress: Boolean = false

    // ReplayGain: tope de volumen de la pista actual tras normalización.
    // La más fuerte queda a 1.0; las más silenciosas a su proporción. Nunca
    // subimos por encima de 1 (Media3 no deja y distorsionaría). Se recalcula
    // al cambiar de canción cuando llega el details del backend.
    @Volatile private var currentRgTarget: Float = 1f

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val controller = mediaController ?: return
            val index = controller.currentMediaItemIndex
            val song = _state.value.queue.getOrNull(index)
            _state.update {
                it.copy(
                    currentIndex = index,
                    currentSong = song,
                    positionMs = 0L,
                    currentCoverUrl = null
                )
            }
            // Reset del flag de "ya logueada" — cada nueva canción puede
            // volver a cruzar el umbral de 30s.
            loggedPlaySongId = null
            if (crossfadeInProgress) {
                // Entrada en B provocada por nuestro seekToNextMediaItem del
                // crossfade (ver startRealCrossfade). El principal arranca
                // en silencio (volume=0 lo setea el starter) y la corutina
                // del crossfade está rampeando 0 → target. NO disparamos
                // scheduleFadeIn aquí porque ya hay un ramp activo; sumarle
                // otro causaría un volumen inestable.
                //
                // currentRgTarget queda a 1f provisional hasta que lleguen
                // los details de B y applyReplayGain lo ajuste. La corutina
                // del crossfade lee currentRgTarget en cada paso.
                currentRgTarget = 1f
            } else {
                // Transición natural (fin de track sin crossfade) o manual
                // (next/previous). Si el usuario quiere gapless y no usa
                // crossfade, la transición tiene que ser INSTANTÁNEA: sin
                // fade-in, sin pausa, sin volumen 0 inicial. Para pistas de
                // álbum que enlazan (Dark Side of the Moon, discos en vivo)
                // es lo que el oído espera. Si crossfade > 0, el fade-in
                // manual sigue teniendo preferencia.
                fadeJob?.cancel()
                fadingOut = false
                currentRgTarget = 1f
                val xf = playerPreferences.crossfadeSeconds.value
                when {
                    xf > 0 -> scheduleFadeIn(controller, xf * 1000L)
                    else -> {
                        // Gapless o simple: volumen inmediato a target.
                        controller.volume = 1f
                    }
                }
            }
            song?.let { fetchCoverFor(it.id) }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            val controller = mediaController ?: return
            if (playbackState == Player.STATE_READY) {
                _state.update { it.copy(durationMs = controller.duration.coerceAtLeast(0L)) }
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.update { it.copy(shuffleEnabled = shuffleModeEnabled) }
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.update {
                it.copy(repeatMode = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                })
            }
        }

        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            // Caso típico: el usuario tenía la canción descargada como file://
            // y la borra desde Descargas o Perfil mientras suena. ExoPlayer
            // falla con IO_FILE_NOT_FOUND al no encontrar el fichero. Antes
            // el audio simplemente paraba sin mensaje. Ahora: si la URI
            // actual es file://, la rearmamos con el endpoint de streaming
            // del backend y seguimos desde la misma posición.
            val controller = mediaController ?: return
            val song = _state.value.currentSong ?: return
            val currentUri = controller.currentMediaItem?.localConfiguration?.uri
            val currentIsLocal = currentUri?.scheme == "file"
            val streamingUri = baseUrl.trimEnd('/') + "/stream/" + song.id

            // Evita bucles: si ya estábamos streameando y el error viene
            // de ahí (p.ej. backend caído), NO replazamos por lo mismo.
            if (!currentIsLocal) return
            if (currentUri?.toString() == streamingUri) return

            android.util.Log.w(
                "PlayerController",
                "Fichero local ausente para songId=${song.id} " +
                    "(errorCode=${error.errorCode}), cayendo a streaming"
            )
            val index = controller.currentMediaItemIndex
            val pos = controller.currentPosition.coerceAtLeast(0L)
            runCatching {
                controller.replaceMediaItem(
                    index,
                    MediaItem.Builder()
                        .setUri(streamingUri)
                        .setMediaId(song.id.toString())
                        .setMediaMetadata(
                            MediaMetadata.Builder()
                                .setTitle(song.title)
                                .setArtist(song.artistsLabel)
                                .build()
                        )
                        .build()
                )
                controller.prepare()
                controller.seekTo(pos)
                controller.playWhenReady = true
            }
        }
    }

    fun connect() {
        // Guard 1: ya hay controller sano → nada que hacer.
        if (mediaController?.isConnected == true) return
        // Guard 2: ya hay una conexión en vuelo → esperamos a esa, no
        // lanzamos otro buildAsync() en paralelo. Si hay un controller
        // "zombi" (no conectado) lo liberamos antes de reconectar.
        if (connecting) return
        mediaController?.let { stale ->
            if (!stale.isConnected) {
                runCatching { stale.removeListener(listener) }
                runCatching { stale.release() }
                mediaController = null
            }
        }

        connecting = true
        val token = SessionToken(
            appContext,
            ComponentName(appContext, PlayerService::class.java)
        )
        val future = MediaController.Builder(appContext, token).buildAsync()
        future.addListener({
            connecting = false
            val controller = runCatching { future.get() }.getOrElse { e ->
                android.util.Log.w("PlayerController",
                    "No pude conectar MediaController: ${e.javaClass.simpleName} ${e.message}")
                // Las acciones pendientes se descartan: no hay a quién
                // mandárselas y guardarlas forever atascaría la UI.
                pendingActions.clear()
                return@addListener
            }
            controller.addListener(listener)
            mediaController = controller
            syncStateFromController(controller)
            startProgressTicker()
            while (pendingActions.isNotEmpty()) {
                val action = pendingActions.removeFirst()
                runCatching { action(controller) }
            }
        }, ContextCompat.getMainExecutor(appContext))
    }

    fun disconnect() {
        stopProgressTicker()
        releaseAuxPlayer()
        mediaController?.removeListener(listener)
        mediaController?.release()
        mediaController = null
        // No limpiamos pendingActions porque el flag `connecting` ya se
        // reseteará en el callback del future si estaba en vuelo. Si acaso,
        // al próximo connect() se intentan disparar.
    }

    /** Cancela el crossfade en curso si lo hay y libera el ExoPlayer auxiliar.
     *  Lo llamamos cuando el usuario toca next/prev manualmente, cambia la
     *  cola, se desconecta, para la reproducción, o al final del crossfade.
     *
     *  Importante: parar el aux ANTES de release() — algunos builds de Media3
     *  tienen un race en el que release() sin stop previo deja el audio
     *  decoder vivo unos ms más (eso explicaba el "no se calla" del user). */
    private fun releaseAuxPlayer() {
        auxPlayer?.let { aux ->
            runCatching { aux.playWhenReady = false }
            runCatching { aux.stop() }
            runCatching { aux.clearMediaItems() }
            runCatching { aux.release() }
        }
        auxPlayer = null
        crossfadeInProgress = false
        fadingOut = false
    }

    private fun syncStateFromController(controller: MediaController) {
        // Clave: cuando la Activity vuelve de background (onStop → disconnect
        // → removeListener) la canción puede haber cambiado sin que nadie
        // lo apunte en _state. Al reconectar hay que re-derivar currentSong
        // y currentCoverUrl desde el currentMediaItemIndex real del
        // MediaController, no quedarnos con lo que había antes.
        val realIndex = controller.currentMediaItemIndex.takeIf { it >= 0 }
            ?: _state.value.currentIndex
        val realSong = _state.value.queue.getOrNull(realIndex)
        val songChanged = realSong?.id != _state.value.currentSong?.id

        _state.update {
            it.copy(
                isPlaying = controller.isPlaying,
                positionMs = controller.currentPosition.coerceAtLeast(0L),
                durationMs = controller.duration.coerceAtLeast(0L),
                currentIndex = realIndex,
                currentSong = realSong ?: it.currentSong,
                // Si la canción cambió, limpiamos la cover vieja para que
                // la UI no muestre la de Rihanna cuando ya suena Lady Gaga.
                currentCoverUrl = if (songChanged) null else it.currentCoverUrl,
                shuffleEnabled = controller.shuffleModeEnabled,
                repeatMode = when (controller.repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }
            )
        }
        // Si tras el sync la cover está vacía y hay canción, pedimos la nueva.
        // No usamos songChanged dentro del update para no mezclar efectos.
        val after = _state.value
        if (after.currentCoverUrl == null && after.currentSong != null) {
            fetchCoverFor(after.currentSong!!.id)
        }
    }

    private fun whenConnected(block: (MediaController) -> Unit) {
        val controller = mediaController
        // isConnected: el MediaController puede estar instanciado pero haber
        // perdido la conexión al service (p. ej. tras bloquear/desbloquear el
        // móvil). En ese estado play/pause fallan en silencio y el usuario
        // ve "el botón no hace nada". Si detectamos el zombi, lo tiramos y
        // encolamos la acción para después de reconectar.
        if (controller != null && controller.isConnected) {
            runCatching { block(controller) }.onFailure { e ->
                android.util.Log.w("PlayerController",
                    "Acción falló sobre MediaController: ${e.message} — reconectando")
                pendingActions.addLast(block)
                runCatching { controller.removeListener(listener) }
                runCatching { controller.release() }
                mediaController = null
                connect()
            }
        } else {
            pendingActions.addLast(block)
            connect()
        }
    }

    fun playQueue(songs: List<SongSummaryDto>, startIndex: Int) {
        if (songs.isEmpty() || startIndex !in songs.indices) return
        _state.update {
            it.copy(
                queue = songs,
                currentIndex = startIndex,
                currentSong = songs[startIndex],
                currentCoverUrl = null,
                positionMs = 0L,
                durationMs = 0L
            )
        }
        fetchCoverFor(songs[startIndex].id)
        whenConnected { controller ->
            val items = songs.map { toMediaItem(it) }
            controller.setMediaItems(items, startIndex, 0L)
            controller.prepare()
            controller.playWhenReady = true
        }
    }

    fun togglePlayPause() = whenConnected { controller ->
        if (controller.isPlaying) {
            controller.pause()
            // Durante un crossfade hay DOS fuentes de audio reproduciendo.
            // Si solo pausamos la principal, el aux sigue sonando sus
            // últimos segundos (lo que el usuario describía como "se queda
            // sonando 12s hasta que acaba"). Propagamos pause/play al aux.
            runCatching { auxPlayer?.playWhenReady = false }
        } else {
            controller.play()
            runCatching { auxPlayer?.playWhenReady = true }
        }
    }

    fun next() = whenConnected { controller ->
        // Si estamos en medio de un crossfade, cancelamos el aux antes de
        // saltar; si no, se quedaría sonando solapado con la nueva pista.
        releaseAuxPlayer()
        // Cancela también la rampa del sleep timer si estaba en los últimos
        // 10 s de fade — la nueva canción empezaría al volumen en que estuviera
        // la rampa (p. ej. 0.3) sin este reset.
        sleepFadeJob?.cancel()
        controller.volume = currentRgTarget
        controller.seekToNextMediaItem()
    }

    fun previous() = whenConnected { controller ->
        releaseAuxPlayer()
        sleepFadeJob?.cancel()
        controller.volume = currentRgTarget
        if (controller.currentPosition > 3_000) controller.seekTo(0)
        else controller.seekToPreviousMediaItem()
    }

    fun seekTo(positionMs: Long) = whenConnected { it.seekTo(positionMs) }

    // ── Cola (visible desde la nueva pantalla QueueScreen) ─────────────────

    /** Salta directamente al índice indicado de la cola actual. */
    fun jumpToIndex(index: Int) = whenConnected { controller ->
        if (index !in 0 until controller.mediaItemCount) return@whenConnected
        releaseAuxPlayer()
        sleepFadeJob?.cancel()
        controller.volume = currentRgTarget
        controller.seekTo(index, 0L)
        controller.play()
    }

    /** Añade una canción al final de la cola. */
    fun addToQueueEnd(song: SongSummaryDto) = whenConnected { controller ->
        controller.addMediaItem(toMediaItem(song))
        _state.update { it.copy(queue = it.queue + song) }
    }

    /** Inserta una canción justo después de la actual ("Reproducir a continuación"). */
    fun playNext(song: SongSummaryDto) = whenConnected { controller ->
        val insertAt = (controller.currentMediaItemIndex + 1)
            .coerceAtMost(controller.mediaItemCount)
        controller.addMediaItem(insertAt, toMediaItem(song))
        _state.update {
            val q = it.queue.toMutableList()
            q.add(insertAt.coerceAtMost(q.size), song)
            it.copy(queue = q)
        }
    }

    /** Elimina una canción de la cola por su índice. */
    fun removeFromQueue(index: Int) = whenConnected { controller ->
        if (index !in 0 until controller.mediaItemCount) return@whenConnected
        if (index == controller.currentMediaItemIndex) {
            // Si borramos la que suena, pasamos al siguiente antes de borrar
            // para no cortar el audio en seco; si era la última, parará solo.
            if (controller.hasNextMediaItem()) controller.seekToNextMediaItem()
            else { controller.pause() }
        }
        controller.removeMediaItem(index)
        _state.update {
            val q = it.queue.toMutableList().apply { if (index < size) removeAt(index) }
            it.copy(
                queue = q,
                currentIndex = controller.currentMediaItemIndex.takeIf { i -> i >= 0 } ?: it.currentIndex
            )
        }
    }

    /** Mueve una canción de la cola entre dos índices. */
    fun moveInQueue(from: Int, to: Int) = whenConnected { controller ->
        val size = controller.mediaItemCount
        if (from !in 0 until size || to !in 0 until size || from == to) return@whenConnected
        controller.moveMediaItem(from, to)
        _state.update {
            val q = it.queue.toMutableList()
            if (from in q.indices) {
                val moved = q.removeAt(from)
                q.add(to.coerceIn(0, q.size), moved)
            }
            it.copy(
                queue = q,
                currentIndex = controller.currentMediaItemIndex.takeIf { i -> i >= 0 } ?: it.currentIndex
            )
        }
    }

    // ── Sleep timer ─────────────────────────────────────────────────────────
    // Tipos de temporizador soportados por la UI. ONLY una minutos duración
    // ("fija") o "al terminar la canción actual". El resto cae al mismo
    // mecanismo: calculamos un timestamp absoluto y un @Scheduled de ticker
    // comprueba cuándo apagar.
    private var sleepTimerJob: Job? = null
    // Rampa del sleep timer separada del ticker principal. Así si el usuario
    // cambia de canción manualmente durante los últimos 10 s podemos cancelar
    // la rampa sin matar el timer entero.
    private var sleepFadeJob: Job? = null

    /** Minutos fijos desde ahora. 0/negativo = cancelar. */
    fun setSleepTimerMinutes(minutes: Int) {
        if (minutes <= 0) { cancelSleepTimer(); return }
        val target = System.currentTimeMillis() + minutes * 60_000L
        armSleepTimer(target)
    }

    /** Para al terminar la pista actual. Útil para "una última canción". */
    fun setSleepTimerAtEndOfTrack() {
        val controller = mediaController ?: return
        val remaining = controller.duration - controller.currentPosition
        if (remaining <= 0) return
        armSleepTimer(System.currentTimeMillis() + remaining)
    }

    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepFadeJob?.cancel()
        sleepTimerJob = null
        sleepFadeJob = null
        // Si el usuario cancela a mitad del fade (los últimos 10 s), el
        // volumen puede haber bajado a cerca de 0 — devolvemos al target.
        mediaController?.volume = currentRgTarget
        _state.update { it.copy(sleepTimerAt = null) }
    }

    private fun armSleepTimer(targetTime: Long) {
        sleepTimerJob?.cancel()
        sleepFadeJob?.cancel()
        _state.update { it.copy(sleepTimerAt = targetTime) }
        sleepTimerJob = scope.launch {
            // Fade de 10 segundos ANTES del target. Un fade de 1s se percibía
            // como un corte seco con un "pop". Diez segundos deja que te
            // duermas sin que la última canción corte en medio del estribillo.
            val fadeDurationMs = 10_000L
            val fadeStart = targetTime - fadeDurationMs
            var fadeArmed = false
            while (true) {
                val now = System.currentTimeMillis()
                if (!fadeArmed && now >= fadeStart && now < targetTime) {
                    fadeArmed = true
                    val c = mediaController
                    if (c != null) {
                        val remaining = (targetTime - now).coerceAtLeast(500L)
                        // Lanzamos la rampa en un job separado para que se
                        // pueda cancelar sin matar el timer entero. Si el
                        // usuario cambia de canción manualmente, next() /
                        // previous() cancelan sleepFadeJob y restauran el
                        // volumen — antes la rampa seguía sobre la nueva.
                        sleepFadeJob = scope.launch {
                            runCatching { rampVolume(c, c.volume, 0f, remaining) }
                        }
                    }
                }
                if (now >= targetTime) {
                    val c = mediaController
                    if (c != null) {
                        c.pause()
                        // Restauramos el volumen para que al reanudar no empiece a 0.
                        c.volume = currentRgTarget
                    }
                    _state.update { it.copy(sleepTimerAt = null) }
                    sleepTimerJob = null
                    sleepFadeJob = null
                    break
                }
                // Tickeamos cada 250 ms cerca del fade para una rampa suave,
                // y cada 1 s cuando falta mucho — ahorra ciclos sin perder precisión.
                delay(if (now >= fadeStart - 1_000) 250 else 1_000)
            }
        }
    }

    fun toggleShuffle() = whenConnected { controller ->
        val newShuffle = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newShuffle
        // Al encender shuffle sin repeat, la cola se agota al terminar el
        // orden aleatorio y Media3 se para. Lo subimos a REPEAT_ALL para
        // que la escucha aleatoria sea "infinita". Si el usuario lo tenía
        // ya en ALL o ONE, respetamos su elección.
        if (newShuffle && controller.repeatMode == Player.REPEAT_MODE_OFF) {
            controller.repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    /**
     * Modo radio: a partir de la canción actual pide una cola de similares
     * al backend y reemplaza la cola de reproducción manteniendo la pista
     * actual al principio para no cortar la escucha. REPEAT_ALL para que
     * siga sonando aunque llegue al final de los similares.
     */
    fun playRadioFromCurrent(onDone: (Boolean, String) -> Unit = { _, _ -> }) {
        val seed = _state.value.currentSong ?: run {
            onDone(false, "No hay canción en reproducción")
            return
        }
        scope.launch {
            runCatching { songRepository.radio(seed.id) }
                .onSuccess { similar ->
                    val queue = listOf(seed) + similar
                    playQueue(queue, 0)
                    whenConnected { it.repeatMode = Player.REPEAT_MODE_ALL }
                    onDone(true, "Radio basada en ${seed.title}")
                }
                .onFailure { onDone(false, it.message ?: "No se pudo generar la radio") }
        }
    }

    /**
     * Entrada única para "aleatorio toda la biblioteca": arranca la cola
     * mezclada, enciende shuffle en Media3 y asegura REPEAT_ALL para que
     * no se detenga al terminar. Pensado para el chip "Aleatorio" de la
     * pantalla Canciones u otras entradas globales.
     */
    fun playShuffleAll(songs: List<SongSummaryDto>) {
        if (songs.isEmpty()) return
        val randomized = songs.shuffled()
        playQueue(randomized, 0)
        whenConnected { controller ->
            controller.shuffleModeEnabled = true
            controller.repeatMode = Player.REPEAT_MODE_ALL
        }
    }

    fun cycleRepeat() = whenConnected { controller ->
        controller.repeatMode = when (controller.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun stop() {
        val controller = mediaController ?: return
        releaseAuxPlayer()
        controller.stop()
        controller.clearMediaItems()
        _state.value = PlayerUiState()
    }

    private fun toMediaItem(song: SongSummaryDto): MediaItem {
        return MediaItem.Builder()
            .setUri(uriFor(song.id))
            .setMediaId(song.id.toString())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artistsLabel)
                    .build()
            )
            .build()
    }

    /**
     * Si la canción está descargada en disco, usamos {@code file://} para
     * ahorrar ancho de banda y permitir sonido offline. Si no, caemos al
     * endpoint de streaming como siempre.
     */
    private fun uriFor(songId: Long): String {
        val local = downloadsRepository.fileFor(songId)
        if (local != null) return local.toURI().toString()
        return baseUrl.trimEnd('/') + "/stream/" + songId
    }

    private fun fetchCoverFor(songId: Long) {
        coverJob?.cancel()
        coverJob = scope.launch {
            runCatching { songRepository.details(songId) }
                .onSuccess { details ->
                    if (_state.value.currentSong?.id == songId) {
                        _state.update { it.copy(currentCoverUrl = details.coverUrl) }
                        // ReplayGain: lo aplicamos aquí porque details es
                        // la primera (y única) petición que trae ese campo.
                        applyReplayGain(details.replayGain)
                    }
                }
        }
    }

    /**
     * Normalización de volumen por canción. {@code replayGain} viene en dB:
     * negativo = la pista está más alta que la referencia (hay que bajarla);
     * positivo = más baja (habría que subirla, pero Media3 no deja pasar de
     * 1.0 sin distorsión, así que capamos ahí). Factor lineal = 10^(dB/20).
     *
     * Si no hay ReplayGain para la canción (Emendatium aún no la analizó),
     * dejamos volumen a 1.0. Al cabo de unos escaneos, todas tendrán gain
     * y las transiciones dejarán de pegar saltos de volumen.
     */
    private fun applyReplayGain(replayGainDb: Double?) {
        val controller = mediaController ?: return
        // Si el usuario desactivó ReplayGain en Ajustes, cada canción suena
        // al volumen "crudo" del fichero. Útil si Emendatium le ha calculado
        // mal el gain a alguna pista y queda demasiado bajita.
        val enabled = playerPreferences.replayGainEnabled.value
        val target = if (!enabled || replayGainDb == null) 1f
        else kotlin.math.min(1f, Math.pow(10.0, replayGainDb / 20.0).toFloat())
        currentRgTarget = target
        if (!fadingOut) controller.volume = target
    }

    private fun startProgressTicker() {
        stopProgressTicker()
        progressJob = scope.launch {
            while (true) {
                val controller = mediaController ?: break
                val position = controller.currentPosition.coerceAtLeast(0L)
                val duration = controller.duration.coerceAtLeast(0L)
                _state.update { it.copy(positionMs = position, durationMs = duration) }
                maybeLogPlay(position)
                maybeStartFadeOut(controller, position, duration)
                delay(300)
            }
        }
    }

    /**
     * Crossfade REAL "estilo Spotify" con pre-buffer anticipado:
     *  1. Cuando quedan ~fadeMs + 2000ms del final de A, creamos el aux
     *     con A pero sin reproducir (`playWhenReady=false`) y hacemos
     *     prepare(). Esto le da 2s de margen para bufferear antes de
     *     que empiece el crossfade. Imprescindible con crossfades
     *     cortos (p.ej. 2s): sin pre-buffer, el aux no se prepara a
     *     tiempo y se oía un silencio en medio.
     *  2. Cuando el aux pasa a STATE_READY, llamamos a armCrossfade:
     *     - Aux salta a la posición ACTUAL de A y arranca a target.
     *     - Principal avanza a B con volume=0 (MediaSession, UI y
     *       letras pasan a B al momento).
     *     - Ramp cruzado durante fadeMs.
     *  3. Al terminar: aux liberado. B sigue sonando normal.
     *
     * Ventaja clave sobre el enfoque anterior: B solo suena desde una
     * fuente (el principal). Imposible que suene doble.
     *
     * Si no hay próxima canción en la cola, cae a fade-out a silencio.
     */
    private fun maybeStartFadeOut(controller: MediaController, position: Long, duration: Long) {
        if (fadingOut || crossfadeInProgress) return
        val xf = playerPreferences.crossfadeSeconds.value
        if (xf <= 0 || duration <= 0L) return
        val fadeMs = xf * 1000L
        val remaining = duration - position
        // Pre-buffer: disparamos 2s antes del momento ideal para dar
        // tiempo al aux a prepararse. Cuando el aux esté READY, ajusta
        // el inicio real del crossfade al punto correcto.
        val preBufferMs = 2000L
        if (remaining !in 1..(fadeMs + preBufferMs)) return

        val state = _state.value
        val naturalNext = state.currentIndex + 1
        // Con "repetir lista" activa, el final de la cola vuelve al principio.
        // Antes aquí dábamos por "fin de cola" y fade a silencio → Media3
        // sí saltaba al 0 por REPEAT_ALL, pero el volumen quedaba en 0 y la
        // primera sonaba "en silencio" → parecía que no volvía.
        val (nextIndex, nextSong) = if (naturalNext < state.queue.size) {
            naturalNext to state.queue[naturalNext]
        } else if (controller.repeatMode == Player.REPEAT_MODE_ALL && state.queue.isNotEmpty()) {
            0 to state.queue[0]
        } else {
            -1 to null
        }

        if (nextSong != null) {
            startRealCrossfade(controller, nextIndex, remaining)
        } else {
            // Fin de cola REAL (REPEAT_OFF o lista vacía): fade-out a silencio.
            if (remaining <= fadeMs) {
                fadingOut = true
                fadeJob?.cancel()
                fadeJob = scope.launch {
                    rampVolume(controller, from = currentRgTarget, to = 0f, durationMs = remaining)
                    // Al terminar el ramp, restauramos el volumen para que
                    // si el usuario vuelve a dar play (Media3 reinicia la
                    // cola en STATE_ENDED) no suene en silencio.
                    runCatching { controller.volume = currentRgTarget }
                    fadingOut = false
                }
            }
        }
    }

    private fun startRealCrossfade(
        controller: MediaController,
        nextIndex: Int,
        remainingMs: Long
    ) {
        val previousSong = _state.value.currentSong ?: return
        val target = currentRgTarget

        crossfadeInProgress = true
        fadingOut = true

        // Pre-buffer de A desde el inicio. NO pre-posicionamos: el aux carga
        // los primeros segundos de A, los descarta, y al estar READY hacemos
        // un seekTo a la posición ACTUAL del principal justo antes de
        // arrancar. Antes usábamos setMediaSource(source, previousPos) en
        // tiempo de preparación: como prepare tardaba 200-500 ms, cuando el
        // aux llegaba a READY el principal ya había avanzado esos ms → al
        // arrancar el aux la pista "retrocedía" ~300 ms = el corte audible
        // que Carlos oía. Ahora el seek ocurre en el momento exacto del
        // arranque y la transición es sin rewind.
        //
        // AudioAttributes iguales al principal + wakeMode NETWORK → evita
        // handshake A2DP de los Bluetooth al cambiar de canción.
        val aux = ExoPlayer.Builder(appContext)
            .setAudioAttributes(
                androidx.media3.common.AudioAttributes.Builder()
                    .setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC)
                    .setUsage(androidx.media3.common.C.USAGE_MEDIA)
                    .build(),
                false // el aux no pide foco: el principal ya lo tiene.
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(androidx.media3.common.C.WAKE_MODE_NETWORK)
            .build()
        val url = uriFor(previousSong.id)
        val auxDataSourceFactory = DefaultDataSource.Factory(
            appContext,
            OkHttpDataSource.Factory(okHttpClient)
        )
        val source = ProgressiveMediaSource
                .Factory(auxDataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
        // Pre-posicionamos con lo mejor que tenemos; el seek definitivo lo
        // haremos en armCrossfade con la posición EN ESE instante. Así el
        // buffering inicial ya empieza cerca del punto correcto y el seek
        // final es un ajuste pequeño (pocos KB) casi instantáneo.
        val initialSeekHint = runCatching { controller.currentPosition }.getOrDefault(0L)
        aux.setMediaSource(source, initialSeekHint)
        aux.volume = 0f  // arrancará silente, la rampa lo sube
        aux.playWhenReady = false
        aux.prepare()
        auxPlayer = aux

        var crossfadeArmed = false

        val auxListener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state != Player.STATE_READY) return
                if (crossfadeArmed) return
                if (!crossfadeInProgress) {
                    runCatching { aux.removeListener(this) }
                    releaseAuxPlayer()
                    return
                }
                crossfadeArmed = true
                runCatching { aux.removeListener(this) }
                armCrossfade(controller, aux, nextIndex, target)
            }
        }
        aux.addListener(auxListener)

        // Timeout de seguridad: si el aux tarda tanto que ya no cabe un
        // crossfade decente antes del fin natural de A, abortamos y
        // dejamos que Media3 haga la transición normal.
        fadeJob?.cancel()
        fadeJob = scope.launch {
            val maxWait = (remainingMs - 500L).coerceAtLeast(300L)
            delay(maxWait)
            if (!crossfadeArmed) {
                runCatching { aux.removeListener(auxListener) }
                releaseAuxPlayer()
            }
        }
    }

    /**
     * Arranca el crossfade una vez el aux está en STATE_READY.
     * Mejoras clave vs. la versión anterior:
     *   1. **Seek sincronizado**: resincronizamos el aux con la posición
     *      ACTUAL del principal justo antes de arrancar → cero rewind.
     *   2. **Rampa equal-power** (sin²/cos²): la suma de energía de las
     *      dos fuentes es constante → el punto medio ya no "se oye más
     *      bajo" como pasaba con la rampa lineal.
     *   3. **Seek del principal retrasado al 70 % del ramp**: cuando el
     *      principal cambia a B, su buffer tiene que cargar → micro-glitch.
     *      Si lo hacemos cuando el aux ya suena fuerte (≥70 % del cross),
     *      el oído enmascara el glitch. Antes el seek era al momento 0 y
     *      la rampa UP subía con B aún bufferreando → "silencio de entrada".
     */
    private fun armCrossfade(
        controller: MediaController,
        aux: ExoPlayer,
        nextIndex: Int,
        target: Float
    ) {
        // 1. Resincroniza el aux con el principal AHORA mismo.
        val principalPos = runCatching { controller.currentPosition }.getOrDefault(0L)
        runCatching { aux.seekTo(principalPos) }

        // 2. Aux empieza a sonar en silencio; la rampa lo sube.
        runCatching { aux.volume = 0f }
        runCatching { aux.playWhenReady = true }

        // 3. El principal baja desde target; se queda tocando A hasta que el
        //    ramp alcanza el 70 %. Ahí cambiamos a B silente para que B
        //    bufferee mientras el aux todavía suena a volumen apreciable;
        //    el oído enmascara el posible glitch del seek.
        runCatching { controller.volume = target }

        val fadeMs = (playerPreferences.crossfadeSeconds.value * 1000L).coerceAtLeast(1000L)
        // Umbral a partir del cual cambiamos el principal a B. 0.7 = el aux
        // va al 95 % de volumen (sin(0.7·π/2) ≈ 0.89 → al cuadrado ≈ 0.79
        // de energía) → suficiente para enmascarar el micro-glitch.
        val switchAt = 0.7f

        fadeJob?.cancel()
        fadeJob = scope.launch {
            val steps = (fadeMs / 40L).coerceAtLeast(1)
            val stepDelay = fadeMs / steps
            var switched = false
            repeat(steps.toInt()) { i ->
                val t = (i + 1f) / steps
                // Equal-power: sin²(t·π/2) + cos²(t·π/2) = 1 → la energía
                // acústica combinada se mantiene constante en todo el ramp.
                val angle = (t * (Math.PI / 2.0)).toFloat()
                val inFactor  = kotlin.math.sin(angle)
                val outFactor = kotlin.math.cos(angle)
                runCatching { aux.volume = (target * inFactor).coerceIn(0f, 1f) }
                if (!switched) {
                    // Principal sigue reproduciendo A con volumen bajando.
                    runCatching { controller.volume = (target * outFactor).coerceIn(0f, 1f) }
                    if (t >= switchAt) {
                        switched = true
                        // Cambio silente a B. onMediaItemTransition ve
                        // crossfadeInProgress=true y NO dispara fade-in extra.
                        runCatching { controller.volume = 0f }
                        runCatching { controller.seekTo(nextIndex, 0L) }
                    }
                }
                // Tras el switch, el principal queda en 0 — B bufferea sin
                // sonar, aux sigue sonando A fading out. Al final del ramp
                // subimos el principal a target y liberamos el aux.
                delay(stepDelay)
            }
            // Final del ramp: principal suena B a volumen target, aux liberado.
            runCatching { controller.volume = currentRgTarget }
            releaseAuxPlayer()
        }
    }

    private fun scheduleFadeIn(controller: MediaController, fadeMs: Long) {
        controller.volume = 0f
        // Fade-in hasta el tope de ReplayGain de la nueva pista. currentRgTarget
        // puede actualizarse a mitad del fade cuando llegan los details; el
        // ramp lee la variable en cada paso vía rampVolume.
        fadeJob = scope.launch {
            rampVolume(controller, from = 0f, to = currentRgTarget, durationMs = fadeMs)
        }
    }

    private suspend fun rampVolume(controller: MediaController, from: Float, to: Float, durationMs: Long) {
        val steps = (durationMs / 50L).coerceAtLeast(1)
        val stepDelay = durationMs / steps
        repeat(steps.toInt()) { i ->
            val t = (i + 1f) / steps
            controller.volume = (from + (to - from) * t).coerceIn(0f, 1f)
            delay(stepDelay)
        }
    }

    /**
     * Dispara el log de reproducción al cruzar los 30s. No cuenta tiempo de
     * pausa como "escucha" porque solo progresa positionMs mientras suena.
     * Un seek manual por encima de 30s también cuenta — es raro en móvil y
     * no merece la complicación de llevar un contador separado por tick.
     */
    private fun maybeLogPlay(positionMs: Long) {
        val songId = _state.value.currentSong?.id ?: return
        if (loggedPlaySongId == songId) return
        if (positionMs < playThresholdMs) return
        loggedPlaySongId = songId
        scope.launch { playHistoryRepository.logPlay(songId) }
    }

    private fun stopProgressTicker() {
        progressJob?.cancel()
        progressJob = null
    }
}
