package com.musarum.mobile.music

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.music.library.AddToPlaylistDialog
import com.musarum.mobile.ui.EmptyView
import com.musarum.mobile.ui.ErrorView
import com.musarum.mobile.ui.LoadingView
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * Pantalla "Canciones" con paginación real:
 *   - El ViewModel mantiene una lista sparse (algunas posiciones son null
 *     hasta que la página correspondiente se carga).
 *   - La UI renderiza placeholders en los null y pide la página en cuanto
 *     el usuario los acerca al viewport.
 *   - El AlphabetJumpBar salta a cualquier índice absoluto; mientras la
 *     página aterriza, se ven placeholders.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongsScreen(
    viewModel: SongListViewModel = viewModel(
        factory = SongListViewModel.factory(LocalAppContainer.current.songRepository)
    )
) {
    val state by viewModel.state.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val container = LocalAppContainer.current
    val me by container.userRepository.me.collectAsState()
    val isOwner = me?.userRole == "OWNER"
    val snackbar = LocalSnackbar.current

    // Para "ir a la canción que suena": necesitamos observar el player y
    // además compartir listState/scope con SongsList (donde vive el LazyColumn).
    val playerState by container.playerController.state.collectAsState()
    val currentSongId = playerState.currentSong?.id
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // Modal + confirmación. Mantenemos la referencia a la canción
    // seleccionada para evitar recomponer la UI con un id suelto.
    var sheetSong by remember { mutableStateOf<SongSummaryDto?>(null) }
    var addSongId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteSong by remember { mutableStateOf<SongSummaryDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Canciones") },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Recargar")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when (val s = state) {
                is SongsUiState.Loading -> LoadingView()
                is SongsUiState.Error -> ErrorView(
                    message = s.message,
                    onRetry = { viewModel.refresh() }
                )
                is SongsUiState.Ready -> if (s.totalCount == 0) {
                    EmptyView("No hay canciones todavía")
                } else {
                    SongsList(
                        state = s,
                        listState = listState,
                        currentSongId = currentSongId,
                        onPlay = { absoluteIndex, song ->
                            // Reproduce la cola con las canciones YA cargadas.
                            // Compactamos a non-null porque PlayerController
                            // no sabe de huecos. Pierde orden absoluto si hay
                            // nulls intermedios, pero la pulsación siempre
                            // suena a la canción tocada; el resto va detrás.
                            val loaded = s.songs.filterNotNull()
                            val startInLoaded = loaded.indexOfFirst { it.id == song.id }
                                .coerceAtLeast(0)
                            container.playerController.playQueue(
                                songs = loaded,
                                startIndex = startInLoaded
                            )
                        },
                        onAdd = { sheetSong = it },
                        onJumpToIndex = { absolute ->
                            viewModel.ensurePageLoadedFor(absolute)
                        },
                        onEnsureLoaded = { viewModel.ensurePageLoadedFor(it) },
                        onShuffleAll = {
                            viewModel.loadShuffled { all ->
                                container.playerController.playShuffleAll(all)
                            }
                        },
                        onJumpToCurrent = {
                            val sid = currentSongId ?: return@SongsList
                            // Guardamos el título antes por si entre tanto
                            // cambia la canción (el mensaje debe citar la que
                            // estábamos buscando, no la que suena ahora).
                            val title = playerState.currentSong?.title ?: "la canción"
                            viewModel.locateSong(
                                songId = sid,
                                onFound = { absolute ->
                                    scope.launch {
                                        listState.animateScrollToItem(absolute + HEADER_ITEMS)
                                    }
                                },
                                onMissing = { reason ->
                                    snackbar("No se pudo localizar \"$title\": $reason")
                                }
                            )
                        }
                    )
                }
            }
        }
    }

    sheetSong?.let { song ->
        SongActionsSheet(
            song = song,
            isOwner = isOwner,
            onDismiss = { sheetSong = null },
            onAddToPlaylist = {
                addSongId = song.id
                sheetSong = null
            },
            onRequestDelete = {
                confirmDeleteSong = song
                sheetSong = null
            }
        )
    }

    if (addSongId != null) {
        AddToPlaylistDialog(
            songId = addSongId!!,
            onDismiss = { addSongId = null }
        )
    }

    confirmDeleteSong?.let { song ->
        ConfirmDeleteSongDialog(
            song = song,
            onDismiss = { confirmDeleteSong = null },
            onConfirm = {
                viewModel.deleteSong(song.id) { _, msg -> snackbar(msg) }
                confirmDeleteSong = null
            }
        )
    }
}

@Composable
private fun SongsList(
    state: SongsUiState.Ready,
    listState: androidx.compose.foundation.lazy.LazyListState,
    currentSongId: Long?,
    onPlay: (absoluteIndex: Int, SongSummaryDto) -> Unit,
    onAdd: (SongSummaryDto) -> Unit,
    onJumpToIndex: (absoluteIndex: Int) -> Unit,
    onEnsureLoaded: (absoluteIndex: Int) -> Unit,
    onShuffleAll: () -> Unit,
    onJumpToCurrent: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()

    // El FAB solo se muestra si hay canción sonando y NO está visible ya en
    // el viewport — así no molesta cuando el usuario ya la tiene delante.
    val currentVisible by remember(currentSongId, state.songs) {
        derivedStateOf {
            if (currentSongId == null) return@derivedStateOf false
            val visible = listState.layoutInfo.visibleItemsInfo
            visible.any { info ->
                val absIndex = info.index - HEADER_ITEMS
                absIndex in state.songs.indices
                    && state.songs[absIndex]?.id == currentSongId
            }
        }
    }
    val showJumpFab = currentSongId != null && !currentVisible

    // Prefetch: cuando el viewport se mueve, pedimos las páginas vecinas.
    // Usamos derivedStateOf para no recomponer en cada frame.
    val visibleRange by remember(state.totalCount) {
        derivedStateOf {
            val visible = listState.layoutInfo.visibleItemsInfo
            if (visible.isEmpty()) IntRange.EMPTY
            else {
                // Las dos primeras entradas son header + chip shuffle.
                val first = (visible.first().index - HEADER_ITEMS).coerceAtLeast(0)
                val last = (visible.last().index - HEADER_ITEMS).coerceAtLeast(0)
                first..last
            }
        }
    }

    LaunchedEffect(listState) {
        snapshotFlow { visibleRange }
            .distinctUntilChanged()
            .collect { range ->
                if (range.isEmpty()) return@collect
                // Página actual + una por delante como buffer: 60 FPS de scroll
                // sin esperar al backend.
                onEnsureLoaded(range.first)
                onEnsureLoaded(range.last)
                onEnsureLoaded(range.last + SongListViewModel.PAGE_SIZE)
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(end = 30.dp)
        ) {
            item {
                LibraryStatsHeader(
                    primary = "${state.totalCount} canciones",
                    secondary = null
                )
            }
            item {
                ShuffleAllChip(onClick = onShuffleAll)
            }
            // Un item por posición absoluta. key = índice (no song.id) porque
            // los nulls no tienen id.
            items(
                count = state.totalCount,
                key = { it }
            ) { absoluteIndex ->
                val song = state.songs.getOrNull(absoluteIndex)
                if (song != null) {
                    SongRow(
                        song = song,
                        onClick = { onPlay(absoluteIndex, song) },
                        onLongPress = { onAdd(song) }
                    )
                } else {
                    SongRowSkeleton()
                }
                HorizontalDivider()
            }
        }

        AlphabetJumpBar(
            letterOffsets = state.letterOffsets,
            onJump = { absolute ->
                onJumpToIndex(absolute)
                coroutineScope.launch {
                    // +HEADER_ITEMS porque la LazyColumn tiene header + chip
                    // antes de la primera canción.
                    listState.animateScrollToItem(absolute + HEADER_ITEMS)
                }
            },
            modifier = Modifier.align(Alignment.CenterEnd)
        )

        if (showJumpFab) {
            SmallFloatingActionButton(
                onClick = onJumpToCurrent,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 16.dp)
            ) {
                Icon(
                    Icons.Filled.MyLocation,
                    contentDescription = "Ir a la canción que suena"
                )
            }
        }
    }
}

/** Header de stats + chip aleatorio = 2 items antes de las canciones. */
private const val HEADER_ITEMS = 2

@Composable
private fun SongRowSkeleton() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(14.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.3f)
                    .height(10.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            )
        }
    }
}

@Composable
private fun ShuffleAllChip(onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.Shuffle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                "Aleatorio",
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SongRow(
    song: SongSummaryDto,
    onClick: () -> Unit,
    onLongPress: (() -> Unit)? = null,
    coverUrl: String? = null
) {
    val base = Modifier
        .fillMaxWidth()
        .let { m ->
            if (onLongPress != null) {
                m.combinedClickable(onClick = onClick, onLongClick = onLongPress)
            } else {
                m.clickable(onClick = onClick)
            }
        }
        .padding(horizontal = 16.dp, vertical = 10.dp)
    Row(
        modifier = base,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Portada con un badge opcional en la esquina inferior derecha que
        // indica si la canción está descargada (reloj=temporal, check=permanente).
        // Lee el estado del DownloadsRepository para que se actualice solo.
        val container = LocalAppContainer.current
        val downloads by container.downloadsRepository.entries.collectAsState()
        val downloadEntry = downloads[song.id]
        Box(modifier = Modifier.size(48.dp)) {
            CoverImage(
                url = coverUrl ?: song.coverUrl,
                size = 48.dp,
                cornerRadius = 8.dp,
                placeholder = Icons.Filled.MusicNote
            )
            if (downloadEntry != null) {
                val isTemp = downloadEntry.isTemporary
                androidx.compose.material3.Surface(
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd),
                    shape = androidx.compose.foundation.shape.CircleShape,
                    color = if (isTemp) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.tertiary,
                    shadowElevation = 2.dp
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (isTemp) Icons.Filled.Schedule
                                          else Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = if (isTemp) MaterialTheme.colorScheme.onPrimary
                                   else MaterialTheme.colorScheme.onTertiary,
                            modifier = Modifier.size(11.dp)
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artistsLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        song.durationMs?.let {
            Spacer(Modifier.width(10.dp))
            Text(
                text = formatDuration(it.toLong()),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

internal fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}
