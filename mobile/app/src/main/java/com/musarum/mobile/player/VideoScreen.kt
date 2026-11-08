package com.musarum.mobile.player

import android.view.SurfaceView
import android.view.ViewGroup
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import com.musarum.mobile.BuildConfig
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.music.ClickableArtists
import com.musarum.mobile.music.library.AddToPlaylistDialog
import kotlinx.coroutines.delay

/**
 * Modo Vídeo del player. Visualmente clonamos NowPlayingScreen (mismo shell,
 * mismo título, mismo ActionsRow, mismos TrackControls) pero donde iría el
 * cover va el SurfaceView del vídeo, servido por /songs/{id}/video.
 *
 * Reproducción:
 *   - Al entrar: pausamos el audio del controller principal y recordamos si
 *     sonaba, para restaurarlo al salir.
 *   - Un ExoPlayer local reproduce el vídeo. Play/pause/seek del TrackControls
 *     operan sobre ESE player, no sobre el audio-only.
 *   - Prev/Next llaman al controller global; cuando la canción activa cambia,
 *     reconstruimos el media source. Si la nueva canción no tiene vídeo
 *     (details.hasVideo == false), volvemos automáticamente a la vista Audio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoScreen(
    onBack: () -> Unit,
    onOpenTrack: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenArtist: (Long) -> Unit = {}
) {
    val container = LocalAppContainer.current
    val controller = container.playerController
    val playerState by controller.state.collectAsState()
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current

    val viewModel: NowPlayingViewModel = viewModel(
        factory = NowPlayingViewModel.factory(
            container.songRepository,
            container.favoriteRepository,
            controller
        )
    )
    val vmState by viewModel.state.collectAsState()

    val song = playerState.currentSong
    if (song == null) {
        onBack()
        return
    }

    // Guardamos si el audio sonaba para restaurarlo al salir. remember{} sin
    // key: solo se evalúa en la primera composición de esta pantalla.
    val wasPlaying = remember { playerState.isPlaying }
    LaunchedEffect(Unit) {
        if (wasPlaying) controller.togglePlayPause()
    }

    var isBuffering by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isVideoPlaying by remember { mutableStateOf(true) }
    var aspectRatio by remember { mutableFloatStateOf(16f / 9f) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableFloatStateOf(0f) }
    var showAddToPlaylist by remember { mutableStateOf(false) }
    var showSleepSheet by remember { mutableStateOf(false) }
    var showMiniLyric by rememberSaveable { mutableStateOf(false) }

    val exoPlayer = remember {
        val dataSourceFactory = OkHttpDataSource.Factory(container.okHttpClient)
        ExoPlayer.Builder(context).build().apply {
            val url = BuildConfig.MUSARUM_BASE_URL.trimEnd('/') + "/songs/" + song.id + "/video"
            val source = ProgressiveMediaSource.Factory(dataSourceFactory)
                .createMediaSource(MediaItem.fromUri(url))
            setMediaSource(source)
            prepare()
            playWhenReady = true
        }
    }

    // Al cambiar de canción (prev/next desde esta pantalla), recargamos el
    // vídeo. Si la nueva canción no tiene vídeo, salimos a Audio: lo detecta
    // vmState.details.hasVideo en cuanto llega la ficha.
    LaunchedEffect(song.id) {
        isBuffering = true
        errorMessage = null
        durationMs = 0L
        positionMs = 0L
        val dataSourceFactory = OkHttpDataSource.Factory(container.okHttpClient)
        val url = BuildConfig.MUSARUM_BASE_URL.trimEnd('/') + "/songs/" + song.id + "/video"
        val source = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(url))
        exoPlayer.setMediaSource(source)
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    // Fallback a Audio cuando la nueva canción activa no tiene vídeo. Solo
    // actuamos si los detalles ya se cargaron para la pista actual.
    LaunchedEffect(vmState.details?.id, vmState.details?.hasVideo) {
        val d = vmState.details ?: return@LaunchedEffect
        if (d.id == song.id && !d.hasVideo) {
            snackbar("Esta canción no tiene vídeo")
            onOpenTrack()
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                isBuffering = state == Player.STATE_BUFFERING
                if (state == Player.STATE_READY) {
                    durationMs = exoPlayer.duration.coerceAtLeast(0L)
                }
            }
            override fun onIsPlayingChanged(playing: Boolean) {
                isVideoPlaying = playing
            }
            override fun onPlayerError(error: PlaybackException) {
                errorMessage = "No se pudo cargar el vídeo: ${error.errorCodeName}"
                isBuffering = false
            }
            override fun onVideoSizeChanged(size: VideoSize) {
                if (size.width > 0 && size.height > 0) {
                    aspectRatio = size.width.toFloat() / size.height.toFloat()
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
            if (wasPlaying && !controller.state.value.isPlaying) {
                controller.togglePlayPause()
            }
        }
    }

    // Ticker local para la posición mientras el vídeo suena. Dejamos de
    // escribir si el usuario está arrastrando el pulgar del slider.
    LaunchedEffect(exoPlayer) {
        while (true) {
            if (!isSeeking) positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            delay(300)
        }
    }

    // Parseo LRC — para el toggle de mini-letra, igual que NowPlaying.
    val syncedLines = remember(vmState.lyrics) { LrcParser.parse(vmState.lyrics) }
    val hasSyncedLyrics = syncedLines.isNotEmpty()
    val currentLineIndex = remember(syncedLines, positionMs) {
        LrcParser.currentIndex(syncedLines, positionMs)
    }
    val currentLine = syncedLines.getOrNull(currentLineIndex)?.text

    PlayerShellScaffold(
        selectedMode = PlayerMode.VIDEO,
        onSelectMode = { mode ->
            when (mode) {
                PlayerMode.TRACK -> onOpenTrack()
                PlayerMode.LYRICS -> onOpenLyrics()
                PlayerMode.INFO -> onOpenInfo()
                PlayerMode.VIDEO -> Unit
            }
        },
        showVideo = true,
        onBack = onBack,
        onSwipeDown = onBack,
        isFavorite = vmState.isFavorite,
        onToggleFavorite = {},
        onAddToPlaylist = {},
        topActionsVisible = false
    ) {
        // ── Hero: ventana del vídeo con el mismo tamaño/forma que el cover ──
        // Mantenemos el cuadrado del cover (320dp max, aspectRatio 1f) y
        // encajamos el vídeo con su aspect-ratio real dentro, con bandas
        // negras cuando no sea cuadrado. Así la transición audio↔vídeo no
        // cambia la geometría de la pantalla.
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 36.dp)
                .pointerInput(Unit) {
                    val threshold = 60.dp.toPx()
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (totalDrag < -threshold) onOpenInfo()
                            else if (totalDrag > threshold) onOpenLyrics()
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f }
                    ) { _, dx -> totalDrag += dx }
                },
            contentAlignment = Alignment.Center
        ) {
            val heroSize = maxWidth.coerceAtMost(320.dp)
            Surface(
                modifier = Modifier.size(heroSize).aspectRatio(1f),
                shape = RoundedCornerShape(20.dp),
                color = Color.Black,
                shadowElevation = 24.dp
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    AndroidView(
                        factory = { ctx ->
                            SurfaceView(ctx).apply {
                                layoutParams = ViewGroup.LayoutParams(
                                    ViewGroup.LayoutParams.MATCH_PARENT,
                                    ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                exoPlayer.setVideoSurfaceView(this)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(aspectRatio)
                    )
                    when {
                        errorMessage != null -> Text(
                            errorMessage ?: "",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp)
                        )
                        isBuffering -> CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Título + artista — swipe horizontal = prev/next ──
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp)
                .pointerInput(Unit) {
                    val threshold = 60.dp.toPx()
                    var totalDrag = 0f
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            if (totalDrag < -threshold) controller.next()
                            else if (totalDrag > threshold) controller.previous()
                            totalDrag = 0f
                        },
                        onDragCancel = { totalDrag = 0f }
                    ) { _, dx -> totalDrag += dx }
                },
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                song.title,
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(6.dp))
            ClickableArtists(
                artists = song.artists,
                onArtistClick = onOpenArtist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Hueco flexible — opcional mini-letra sincronizada en el centro.
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (showMiniLyric && hasSyncedLyrics && !currentLine.isNullOrBlank()) {
                Text(
                    text = currentLine,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 32.dp)
                )
            }
        }

        VideoActionsRow(
            isFavorite = vmState.isFavorite,
            onToggleFavorite = {
                // Sin snackbar: el cambio de color del ♥ ya es feedback claro.
                viewModel.toggleFavorite()
            },
            onAddToPlaylist = { showAddToPlaylist = true },
            miniLyricAvailable = hasSyncedLyrics,
            miniLyricActive = showMiniLyric && hasSyncedLyrics,
            onToggleMiniLyric = {
                if (hasSyncedLyrics) showMiniLyric = !showMiniLyric
            },
            onStartRadio = {
                controller.playRadioFromCurrent { _, msg -> snackbar(msg) }
            },
            sleepTimerActive = playerState.sleepTimerAt != null,
            onOpenSleepTimer = { showSleepSheet = true },
            downloadState = run {
                val entries by container.downloadsRepository.entries.collectAsState()
                val inProgress by container.downloadsManager.inProgress.collectAsState()
                when {
                    inProgress.contains(song.id) -> DownloadState.IN_PROGRESS
                    entries[song.id]?.isTemporary == true -> DownloadState.TEMPORARY
                    entries[song.id] != null -> DownloadState.PERMANENT
                    else -> DownloadState.NOT_DOWNLOADED
                }
            },
            onDownload = {
                val entry = container.downloadsRepository.get(song.id)
                when {
                    entry == null -> {
                        container.downloadsManager.download(song, forceTemporary = true)
                        snackbar("Descargando (temporal 24h)")
                    }
                    entry.isTemporary -> {
                        container.downloadsManager.setKeepPermanent(song.id, true)
                        snackbar("Descarga marcada como permanente")
                    }
                    else -> {
                        container.downloadsManager.remove(song.id)
                        snackbar("Descarga eliminada")
                    }
                }
            }
        )

        VideoTrackControls(
            isPlaying = isVideoPlaying,
            positionMs = if (isSeeking) seekValue.toLong() else positionMs,
            durationMs = durationMs,
            shuffleEnabled = playerState.shuffleEnabled,
            repeatMode = playerState.repeatMode,
            hasPrevious = playerState.hasPrevious,
            hasNext = playerState.hasNext,
            onTogglePlayPause = { exoPlayer.playWhenReady = !exoPlayer.playWhenReady },
            onSeekStart = { isSeeking = true; seekValue = it },
            onSeekChange = { seekValue = it },
            onSeekEnd = {
                exoPlayer.seekTo(seekValue.toLong())
                positionMs = seekValue.toLong()
                isSeeking = false
            },
            onPrevious = { controller.previous() },
            onNext = { controller.next() },
            onToggleShuffle = { controller.toggleShuffle() },
            onCycleRepeat = { controller.cycleRepeat() }
        )

        Spacer(Modifier.height(16.dp))

        if (showSleepSheet) {
            SleepTimerSheet(
                activeAt = playerState.sleepTimerAt,
                onDismiss = { showSleepSheet = false },
                onPickMinutes = { mins ->
                    controller.setSleepTimerMinutes(mins)
                    snackbar(if (mins > 0) "Dormirá en $mins min" else "Temporizador cancelado")
                    showSleepSheet = false
                },
                onEndOfTrack = {
                    controller.setSleepTimerAtEndOfTrack()
                    snackbar("Dormirá al terminar la canción")
                    showSleepSheet = false
                },
                onCancel = {
                    controller.cancelSleepTimer()
                    snackbar("Temporizador cancelado")
                    showSleepSheet = false
                }
            )
        }
    }

    if (showAddToPlaylist) {
        AddToPlaylistDialog(
            songId = song.id,
            onDismiss = { showAddToPlaylist = false }
        )
    }
}

/**
 * Idéntico a ActionsRow de NowPlaying — se duplica aquí porque aquélla es
 * privada al archivo. Lo mantenemos sincronizado si cambia el orden/iconos.
 */
@Composable
private fun VideoActionsRow(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    miniLyricAvailable: Boolean,
    miniLyricActive: Boolean,
    onToggleMiniLyric: () -> Unit,
    onStartRadio: () -> Unit,
    sleepTimerActive: Boolean,
    onOpenSleepTimer: () -> Unit,
    downloadState: DownloadState,
    onDownload: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        VideoGhostCircleButton(
            onClick = onToggleMiniLyric,
            contentDescription = if (miniLyricActive) "Ocultar letra" else "Mostrar letra encima"
        ) {
            Icon(
                Icons.Filled.Lyrics,
                contentDescription = null,
                tint = when {
                    !miniLyricAvailable -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
                    miniLyricActive     -> MaterialTheme.colorScheme.primary
                    else                -> MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.size(6.dp))
        // Overflow idéntico al de NowPlaying — radio + sleep aquí dentro
        // para no saturar la fila sobre el vídeo.
        androidx.compose.foundation.layout.Box {
            var showMore by remember { mutableStateOf(false) }
            VideoGhostCircleButton(
                onClick = { showMore = true },
                contentDescription = "Más acciones"
            ) {
                Icon(
                    Icons.Filled.MoreHoriz,
                    contentDescription = null,
                    tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
            DropdownMenu(
                expanded = showMore,
                onDismissRequest = { showMore = false },
                offset = androidx.compose.ui.unit.DpOffset(x = (-8).dp, y = 8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                DropdownMenuItem(
                    text = { Text("Radio basada en esta") },
                    leadingIcon = { Icon(Icons.Filled.Radio, null) },
                    onClick = { showMore = false; onStartRadio() }
                )
                DropdownMenuItem(
                    text = {
                        Text(if (sleepTimerActive) "Temporizador activo"
                             else "Temporizador de apagado")
                    },
                    leadingIcon = {
                        Icon(
                            Icons.Filled.Bedtime,
                            null,
                            tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    onClick = { showMore = false; onOpenSleepTimer() }
                )
            }
        }
        Spacer(Modifier.size(6.dp))
        VideoGhostCircleButton(onClick = onDownload, contentDescription = downloadContentDescription(downloadState)) {
            Icon(
                imageVector = downloadIcon(downloadState),
                contentDescription = null,
                tint = downloadTint(downloadState),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.size(6.dp))
        VideoGhostCircleButton(onClick = onAddToPlaylist, contentDescription = "Añadir a playlist") {
            Icon(Icons.Filled.PlaylistAdd, null, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.size(6.dp))
        VideoGhostCircleButton(onClick = onToggleFavorite, contentDescription = "Favorita") {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                contentDescription = null,
                tint = if (isFavorite) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

@Composable
private fun VideoGhostCircleButton(
    onClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        IconButton(onClick = onClick, modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

/**
 * Clon visual de TrackControls de NowPlaying, pero con callbacks parametri-
 * zados: aquí el play/pause y el seek operan sobre el ExoPlayer local del
 * vídeo, no sobre el audio-only del PlayerController. Los iconos restantes
 * (shuffle/prev/next/repeat) sí van al controller global, porque la cola
 * de canciones vive allí.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoTrackControls(
    isPlaying: Boolean,
    positionMs: Long,
    durationMs: Long,
    shuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekStart: (Float) -> Unit,
    onSeekChange: (Float) -> Unit,
    onSeekEnd: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onToggleShuffle: () -> Unit,
    onCycleRepeat: () -> Unit
) {
    val totalMs = durationMs.coerceAtLeast(0L)
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Slider(
                value = positionMs.toFloat().coerceAtMost(totalMs.toFloat()),
                valueRange = 0f..(totalMs.toFloat().coerceAtLeast(1f)),
                enabled = totalMs > 0,
                onValueChange = { v -> onSeekStart(v); onSeekChange(v) },
                onValueChangeFinished = onSeekEnd,
                modifier = Modifier.fillMaxWidth().height(26.dp),
                thumb = { QuarterNoteThumb() },
                track = { sliderState ->
                    val range = sliderState.valueRange
                    val span = range.endInclusive - range.start
                    val fraction = if (span > 0f)
                        ((sliderState.value - range.start) / span).coerceIn(0f, 1f)
                    else 0f
                    Box(
                        modifier = Modifier.fillMaxWidth().height(26.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(fraction)
                                    .fillMaxHeight()
                                    .background(MaterialTheme.colorScheme.primary)
                            )
                        }
                    }
                }
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    formatMs(positionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    formatMs(totalMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onToggleShuffle) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Aleatorio",
                    tint = if (shuffleEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onPrevious,
                enabled = hasPrevious,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Filled.SkipPrevious,
                    contentDescription = "Anterior",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Surface(
                modifier = Modifier.size(70.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                shadowElevation = 8.dp
            ) {
                IconButton(
                    onClick = onTogglePlayPause,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            IconButton(
                onClick = onNext,
                enabled = hasNext,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Siguiente",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = onCycleRepeat) {
                Icon(
                    imageVector = if (repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne
                    else Icons.Filled.Repeat,
                    contentDescription = "Repetir",
                    tint = if (repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
