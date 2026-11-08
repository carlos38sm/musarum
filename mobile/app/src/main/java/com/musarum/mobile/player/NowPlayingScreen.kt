package com.musarum.mobile.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlinx.coroutines.launch
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.PlaylistPlay
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.unit.DpOffset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.music.library.AddToPlaylistDialog

@Composable
fun NowPlayingScreen(
    onBack: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenVideo: () -> Unit,
    onOpenFavorites: () -> Unit = {},
    onOpenQueue: () -> Unit = {},
    onOpenArtist: (Long) -> Unit = {}
) {
    val container = LocalAppContainer.current
    val controller = container.playerController
    val playerState by controller.state.collectAsState()

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

    var showAddToPlaylist by remember { mutableStateOf(false) }
    // Para la acción OWNER "eliminar del disco" del dropdown.
    val me by container.userRepository.me.collectAsState()
    val isOwner = me?.userRole == "OWNER"
    var confirmDelete by remember { mutableStateOf(false) }
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    // Toggle de mini-letra persistente mientras no se cambie de sesión. El
    // botón solo responde si hay lyrics sincronizadas en la pista actual.
    var showMiniLyric by rememberSaveable { mutableStateOf(false) }

    // Parseo LRC — vacío si no hay o si no está sincronizada.
    val syncedLines = remember(vmState.lyrics) { LrcParser.parse(vmState.lyrics) }
    val hasSyncedLyrics = syncedLines.isNotEmpty()
    val currentLineIndex = remember(syncedLines, playerState.positionMs) {
        LrcParser.currentIndex(syncedLines, playerState.positionMs)
    }
    val currentLine = syncedLines.getOrNull(currentLineIndex)?.text

    PlayerShellScaffold(
        selectedMode = PlayerMode.TRACK,
        onSelectMode = { mode ->
            when (mode) {
                PlayerMode.TRACK -> Unit
                PlayerMode.LYRICS -> onOpenLyrics()
                PlayerMode.INFO -> onOpenInfo()
                PlayerMode.VIDEO -> onOpenVideo()
            }
        },
        showVideo = vmState.details?.hasVideo == true,
        onBack = onBack,
        onSwipeDown = onBack, // deslizar hacia abajo cierra el player
        isFavorite = vmState.isFavorite,
        onToggleFavorite = {}, // ya no se usa en el shell — las acciones viven abajo
        onAddToPlaylist = {}, // idem
        topActionsVisible = false
    ) {
        // ── Hero: cover grande ──
        // Swipe izquierda sobre la foto → Info (destino a la derecha en la píldora)
        // Swipe derecha sobre la foto → Letra (destino a la izquierda en la píldora)
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
            val coverSize = maxWidth.coerceAtMost(320.dp)
            Surface(
                modifier = Modifier.size(coverSize).aspectRatio(1f),
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                shadowElevation = 24.dp
            ) {
                CoverImage(
                    url = playerState.currentCoverUrl,
                    size = coverSize,
                    cornerRadius = 20.dp
                )
            }
        }

        Spacer(Modifier.height(28.dp))

        // ── Título + artista ──
        // Swipe izquierda sobre el título → siguiente canción
        // Swipe derecha sobre el título → canción anterior
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
            // Cada nombre de artista es su propio link independiente:
            // "Shakira, Maluma" → tap en Shakira → Shakira; tap en Maluma →
            // Maluma. Sin BottomSheet intermedio.
            com.musarum.mobile.music.ClickableArtists(
                artists = song.artists,
                onArtistClick = onOpenArtist,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // Espacio flexible entre título/artista y las acciones. Si el mini-
        // lyric está activo y hay LRC, pintamos la línea actual centrada
        // en ese hueco; si no, es solo aire para empujar los controles al
        // fondo del hero.
        Box(
            modifier = Modifier.fillMaxWidth().weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (showMiniLyric && hasSyncedLyrics && !currentLine.isNullOrBlank()) {
                Text(
                    text = currentLine,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
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

        // Fila de acciones secundarias justo encima de la barra de progreso.
        // Orden: mini-letra · más (cola/radio/sleep) · descargar · añadir · ♥.
        // Alineadas a la derecha para no competir con la bolita del slider.
        var showSleepSheet by remember { mutableStateOf(false) }
        ActionsRow(
            isFavorite = vmState.isFavorite,
            onToggleFavorite = {
                // Sin snackbar: el cambio de color del ♥ ya es feedback claro.
                viewModel.toggleFavorite()
            },
            onLongPressFavorite = onOpenFavorites,
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
            onOpenQueue = onOpenQueue,
            isOwner = isOwner,
            onRequestDelete = { confirmDelete = true },
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
                // Ciclo claro de 3 estados en este botón, INDEPENDIENTE del
                // toggle "Descargas temporales por defecto" del Perfil
                // (que afecta a descargas automáticas desde playlist/sheet).
                // Aquí: nube → temporal → permanente → nube.
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

        TrackControls(controller, playerState)

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

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            icon = {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Eliminar del disco") },
            text = {
                Text(
                    "Se borrará «${song.title}» del servidor (fichero, " +
                    "favoritos, playlists y reproducciones). " +
                    "Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    val idToDelete = song.id
                    scope.launch {
                        runCatching { container.songRepository.delete(idToDelete) }
                            .onSuccess {
                                snackbar("Eliminada")
                                // Avanza a la siguiente de la cola; si era la
                                // única, vuelve a la pantalla anterior.
                                runCatching { controller.next() }
                                    .onFailure { onBack() }
                            }
                            .onFailure { t ->
                                snackbar("No se pudo eliminar: ${t.message ?: "error"}")
                            }
                    }
                }) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancelar") }
            }
        )
    }
}

/**
 * Scaffold compartido para las 3 pantallas del player (Pista, Letra, Info).
 * Pinta el fondo con gradiente ámbar sutil, el top con los iconos ghost y
 * la píldora de modos justo debajo. El resto lo rellena el contenido.
 */
@Composable
fun PlayerShellScaffold(
    selectedMode: PlayerMode,
    onSelectMode: (PlayerMode) -> Unit,
    onBack: () -> Unit,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onSwipeDown: (() -> Unit)? = null,
    showVideo: Boolean = false,
    // Si el caller va a pintar fav/add en otro sitio (p. ej. NowPlaying los
    // lleva sobre la progress bar), que oculte este bloque superior.
    topActionsVisible: Boolean = true,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background

    // Fondo dinámico: lee la portada actual del PlayerController y extrae su
    // color dominante con Palette. Mientras llega, se usa el primary del tema
    // como fallback. La transición entre canciones se interpola.
    val playerState by com.musarum.mobile.LocalAppContainer.current
        .playerController.state.collectAsState()
    val dominant by rememberDominantCoverColor(
        coverUrl = playerState.currentCoverUrl,
        fallback = primary
    )

    // Drag-to-dismiss interactivo: la Column interior sigue al dedo mientras
    // arrastras hacia abajo (controles incluidos — es un panel entero que
    // baja, no una capa parcial). Si al soltar superas ~22% de pantalla,
    // animamos hasta el borde y disparamos onSwipeDown SIN resetear el
    // offset: así el panel ya está fuera cuando el pop de nav termina y no
    // hay parpadeo.
    val scope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val dismissThresholdPx = screenHeightPx * 0.22f
    var dragOffset by remember { mutableFloatStateOf(0f) }

    val swipeDownModifier = if (onSwipeDown != null) {
        Modifier.pointerInput(Unit) {
            detectVerticalDragGestures(
                onDragEnd = {
                    if (dragOffset > dismissThresholdPx) {
                        scope.launch {
                            val anim = Animatable(dragOffset)
                            anim.animateTo(screenHeightPx, tween(220)) {
                                dragOffset = value
                            }
                            // No reseteamos dragOffset a 0: dejar el panel
                            // fuera de pantalla evita un parpadeo entre la
                            // animación y el pop de la navegación.
                            onSwipeDown()
                        }
                    } else {
                        scope.launch {
                            val anim = Animatable(dragOffset)
                            anim.animateTo(0f, spring()) { dragOffset = value }
                        }
                    }
                },
                onDragCancel = {
                    scope.launch {
                        val anim = Animatable(dragOffset)
                        anim.animateTo(0f, spring()) { dragOffset = value }
                    }
                }
            ) { _, dy ->
                // Solo deja arrastrar hacia abajo — hacia arriba lo ignora
                // (coerce ≥ 0) para no desenclavar la pantalla.
                dragOffset = (dragOffset + dy).coerceAtLeast(0f)
            }
        }
    } else Modifier

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                // Degradado "mimetizado con la portada": arriba el color
                // dominante con mucho peso, baja a un tinte sutil a medio
                // camino y acaba en el background del tema para no reñir
                // con el texto de abajo. El Palette ya nos da un color
                // saturado; aun así metemos alfa para evitar un bloque
                // plano chillón.
                Brush.verticalGradient(
                    0.0f to dominant.copy(alpha = 0.75f),
                    0.35f to dominant.copy(alpha = 0.28f),
                    0.65f to background,
                    1.0f to background
                )
            )
            .then(swipeDownModifier)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .offset { IntOffset(0, dragOffset.toInt()) }
                .padding(WindowInsets.systemBars.asPaddingValues())
        ) {
            // Handle estilo iOS en la cima — reemplaza al chevron. Solo
            // afordance visual del swipe-down; no es clickable.
            HandleBar()

            if (topActionsVisible) {
                TopActions(
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    onAddToPlaylist = onAddToPlaylist
                )
            }

            Spacer(Modifier.height(if (topActionsVisible) 8.dp else 16.dp))

            ModePill(
                selected = selectedMode,
                onSelect = onSelectMode,
                showVideo = showVideo
            )

            Spacer(Modifier.height(28.dp))

            content()
        }
    }
}

/**
 * Barrita iOS-style arriba del todo. 36x4dp, redondeada, color onSurfaceVariant
 * a baja opacidad. Solo visual — el swipe-down lo captura el Box padre.
 */
@Composable
private fun HandleBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(width = 36.dp, height = 4.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f))
        )
    }
}

@Composable
private fun TopActions(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 0.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.End
    ) {
        GhostCircleButton(onClick = onAddToPlaylist, contentDescription = "Añadir a playlist") {
            Icon(Icons.Filled.PlaylistAdd, null, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(6.dp))
        GhostCircleButton(onClick = onToggleFavorite, contentDescription = "Favorita") {
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

/**
 * Fila de acciones secundarias dentro de NowPlaying: Añadir a playlist,
 * Favorita y toggle de Mini-letra. Alineadas a la derecha, encima de la
 * barra de progreso. Mini-letra queda deshabilitada si la pista no trae
 * LRC sincronizada.
 */
/**
 * Estados visuales del botón de descarga. Mapean a:
 *   NOT_DOWNLOADED → nube con flecha, acento neutro
 *   IN_PROGRESS    → icono "descargando", acento primary
 *   TEMPORARY      → check-con-reloj (dura 24h), acento primary
 *   PERMANENT      → check sólido verde-tertiary (sin caducidad)
 */
internal enum class DownloadState { NOT_DOWNLOADED, IN_PROGRESS, TEMPORARY, PERMANENT }

@Composable
private fun ActionsRow(
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onLongPressFavorite: () -> Unit,
    onAddToPlaylist: () -> Unit,
    miniLyricAvailable: Boolean,
    miniLyricActive: Boolean,
    onToggleMiniLyric: () -> Unit,
    onStartRadio: () -> Unit,
    sleepTimerActive: Boolean,
    onOpenSleepTimer: () -> Unit,
    onOpenQueue: () -> Unit,
    isOwner: Boolean,
    onRequestDelete: () -> Unit,
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
        GhostCircleButton(
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
        Spacer(Modifier.width(6.dp))
        // Overflow: antes había 3 iconos (Radio / Sleep / Cola) aquí; ahora
        // viven en este menú desplegable para no saturar la fila. El icono
        // se tinta en primary si hay sleep activo, para que el OWNER vea
        // "hay algo activo ahí dentro" sin abrirlo.
        Box {
            var showMore by remember { mutableStateOf(false) }
            GhostCircleButton(
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
            // Offset: el ⋯ vive cerca del centro de la fila y sin este nudge
            // el popup caía anclado a la izquierda del botón y quedaba feo
            // pegado al borde. Lo desplazamos ligeramente hacia la derecha +
            // abajo para que respire sobre el botón. El modifier le da ancho
            // mínimo, esquinas redondeadas y usa surfaceContainer para que
            // contraste bien con el gradiente del shell.
            DropdownMenu(
                expanded = showMore,
                onDismissRequest = { showMore = false },
                offset = DpOffset(x = (-8).dp, y = 8.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            ) {
                DropdownMenuItem(
                    text = { Text("Cola de reproducción") },
                    leadingIcon = { Icon(Icons.Filled.PlaylistPlay, null) },
                    onClick = { showMore = false; onOpenQueue() }
                )
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
                if (isOwner) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                "Eliminar del disco",
                                color = MaterialTheme.colorScheme.error
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Filled.Delete,
                                null,
                                tint = MaterialTheme.colorScheme.error
                            )
                        },
                        onClick = { showMore = false; onRequestDelete() }
                    )
                }
            }
        }
        Spacer(Modifier.width(6.dp))
        GhostCircleButton(onClick = onDownload, contentDescription = downloadContentDescription(downloadState)) {
            Icon(
                imageVector = downloadIcon(downloadState),
                contentDescription = null,
                tint = downloadTint(downloadState),
                modifier = Modifier.size(22.dp)
            )
        }
        Spacer(Modifier.width(6.dp))
        GhostCircleButton(onClick = onAddToPlaylist, contentDescription = "Añadir a playlist") {
            Icon(Icons.Filled.PlaylistAdd, null, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(6.dp))
        // Favorita con long-press para abrir Favoritas de Biblioteca —
        // acceso directo pedido por Carlos (nov 2026). Tap normal = toggle.
        GhostCircleButtonLong(
            onClick = onToggleFavorite,
            onLongClick = onLongPressFavorite,
            contentDescription = "Favorita (mantén pulsado para ver tus favoritas)"
        ) {
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
private fun GhostCircleButton(
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
 * Variante del GhostCircleButton con tap y long-press. IconButton no soporta
 * long-press nativo, así que caemos al patrón combinedClickable sobre un Box
 * del mismo tamaño para preservar la geometría del botón "fantasma".
 */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun GhostCircleButtonLong(
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    contentDescription: String,
    content: @Composable () -> Unit
) {
    Surface(
        modifier = Modifier.size(42.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick,
                    role = androidx.compose.ui.semantics.Role.Button
                ),
            contentAlignment = Alignment.Center
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TrackControls(controller: PlayerController, state: PlayerUiState) {
    var isSeeking by remember { mutableStateOf(false) }
    var seekValue by remember { mutableStateOf(0f) }
    val displayPos = if (isSeeking) seekValue.toLong() else state.positionMs
    val totalMs = state.durationMs.coerceAtLeast(0L)

    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Barra fina estilo Spotify con nota negra como pulgar. El track va
            // de 2dp y el pulgar queda centrado verticalmente respecto a él.
            Slider(
                value = displayPos.toFloat().coerceAtMost(totalMs.toFloat()),
                valueRange = 0f..(totalMs.toFloat().coerceAtLeast(1f)),
                enabled = totalMs > 0,
                onValueChange = { isSeeking = true; seekValue = it },
                onValueChangeFinished = {
                    controller.seekTo(seekValue.toLong())
                    isSeeking = false
                },
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
                    formatMs(displayPos),
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
            IconButton(onClick = { controller.toggleShuffle() }) {
                Icon(
                    Icons.Filled.Shuffle,
                    contentDescription = "Aleatorio",
                    tint = if (state.shuffleEnabled) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = { controller.previous() },
                enabled = state.hasPrevious,
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
                    onClick = { controller.togglePlayPause() },
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                        contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                        modifier = Modifier.size(36.dp),
                        tint = MaterialTheme.colorScheme.onPrimary
                    )
                }
            }
            IconButton(
                onClick = { controller.next() },
                enabled = state.hasNext,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Siguiente",
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            IconButton(onClick = { controller.cycleRepeat() }) {
                Icon(
                    imageVector = if (state.repeatMode == RepeatMode.ONE) Icons.Filled.RepeatOne
                    else Icons.Filled.Repeat,
                    contentDescription = "Repetir",
                    tint = if (state.repeatMode != RepeatMode.OFF) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun downloadIcon(state: DownloadState) = when (state) {
    DownloadState.NOT_DOWNLOADED -> Icons.Filled.Download
    DownloadState.IN_PROGRESS    -> Icons.Filled.Downloading
    // Antes TEMPORARY usaba DownloadDone (check pequeño) que se parecía
    // demasiado a CheckCircle de PERMANENT. Reloj deja claro "esto caduca".
    DownloadState.TEMPORARY      -> Icons.Filled.Schedule
    DownloadState.PERMANENT      -> Icons.Filled.CheckCircle
}

@Composable
internal fun downloadTint(state: DownloadState) = when (state) {
    DownloadState.NOT_DOWNLOADED -> MaterialTheme.colorScheme.onSurface
    DownloadState.IN_PROGRESS    -> MaterialTheme.colorScheme.primary
    DownloadState.TEMPORARY      -> MaterialTheme.colorScheme.primary
    DownloadState.PERMANENT      -> MaterialTheme.colorScheme.tertiary
}

internal fun downloadContentDescription(state: DownloadState) = when (state) {
    DownloadState.NOT_DOWNLOADED -> "Descargar para offline"
    DownloadState.IN_PROGRESS    -> "Descargando…"
    DownloadState.TEMPORARY      -> "Descarga temporal — tocar para mantener permanente"
    DownloadState.PERMANENT      -> "Descargada — tocar para eliminar"
}

internal fun formatMs(ms: Long): String {
    val totalSeconds = (ms / 1000).coerceAtLeast(0)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

/**
 * Pulgar del slider: una corchea (nota musical con bandera) dibujada en
 * Canvas. Cabeza elíptica inclinada ~-18°, plica vertical a la derecha y
 * banderilla curva arriba. Todo con el color primary. Al ser Canvas, se
 * mantiene nítida a cualquier escala y da identidad propia al reproductor.
 */
@Composable
internal fun QuarterNoteThumb() {
    val color = MaterialTheme.colorScheme.primary
    Canvas(modifier = Modifier.size(width = 14.dp, height = 24.dp)) {
        val w = size.width
        val h = size.height

        // Plica (barra vertical a la derecha de la cabeza).
        drawRect(
            color = color,
            topLeft = Offset(w * 0.60f, h * 0.08f),
            size = Size(w * 0.14f, h * 0.70f)
        )

        // Banderilla: curva Bezier desde la punta superior de la plica
        // hacia abajo-derecha. Da el perfil característico de corchea.
        val flagPath = Path().apply {
            moveTo(w * 0.74f, h * 0.08f)
            cubicTo(
                w * 1.10f, h * 0.18f,
                w * 1.05f, h * 0.38f,
                w * 0.80f, h * 0.42f
            )
        }
        drawPath(
            path = flagPath,
            color = color,
            style = Stroke(
                width = w * 0.14f,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )

        // Cabeza elíptica inclinada ~-18° — típica de nota en una partitura.
        // rotate() del drawscope es una extensión del DrawScope, por eso se
        // invoca directamente sin receiver explícito.
        rotate(
            degrees = -18f,
            pivot = Offset(w * 0.44f, h * 0.82f)
        ) {
            drawOval(
                color = color,
                topLeft = Offset(w * 0.02f, h * 0.68f),
                size = Size(w * 0.80f, h * 0.28f)
            )
        }
    }
}
