package com.musarum.mobile.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.music.library.AddToPlaylistDialog

@Composable
fun LyricsScreen(
    onBack: () -> Unit,
    onOpenTrack: () -> Unit,
    onOpenInfo: () -> Unit,
    onOpenVideo: () -> Unit
) {
    val container = LocalAppContainer.current
    val controller = container.playerController
    val playerState by controller.state.collectAsState()
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


    // Parseo del LRC: lo recalculamos solo cuando cambia el texto crudo
    val syncedLines by remember(vmState.lyrics) {
        mutableStateOf(LrcParser.parse(vmState.lyrics))
    }

    PlayerShellScaffold(
        selectedMode = PlayerMode.LYRICS,
        onSelectMode = { mode ->
            when (mode) {
                PlayerMode.TRACK -> onOpenTrack()
                PlayerMode.LYRICS -> Unit
                PlayerMode.INFO -> onOpenInfo()
                PlayerMode.VIDEO -> onOpenVideo()
            }
        },
        showVideo = vmState.details?.hasVideo == true,
        onBack = onBack,
        // Fav y add-to-playlist viven solo en la vista de audio: aquí sería
        // ruido. El scaffold respeta topActionsVisible=false y no los pinta.
        isFavorite = false,
        onToggleFavorite = {},
        onAddToPlaylist = {},
        topActionsVisible = false
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                song.artistsLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(20.dp))

        // weight(1f) deja que el Box del contenido ocupe el hueco restante
        // de la Column del scaffold SIN empujar fuera la Row del botón que
        // va justo debajo. Con fillMaxSize se cargaba toda la altura y la
        // Row quedaba por debajo del borde (invisible).
        Box(modifier = Modifier.fillMaxWidth().weight(1f).padding(horizontal = 28.dp)) {
            when {
                vmState.detailsLoading -> Center { Progress() }
                vmState.lyricsLoading -> Center {
                    // El spinner solo no explica por qué tarda. Si no había
                    // local, el backend está hablando con LrcLib (hasta 7s
                    // de timeout). Avisar al usuario de qué está pasando
                    // evita la sensación de "la app se ha colgado".
                    androidx.compose.foundation.layout.Column(
                        horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally
                    ) {
                        Progress()
                        androidx.compose.foundation.layout.Spacer(
                            androidx.compose.ui.Modifier.height(10.dp)
                        )
                        Text(
                            "Buscando letra…",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                vmState.lyricsError != null -> Center {
                    NoLyricsWithRefresh(
                        onRefresh = {
                            viewModel.refreshLyrics { _, msg -> snackbar(msg) }
                        }
                    )
                }
                syncedLines.isNotEmpty() -> SyncedLyricsView(
                    lines = syncedLines,
                    positionMs = playerState.positionMs,
                    onSeek = { timeMs -> controller.seekTo(timeMs) }
                )
                !vmState.lyrics.isNullOrBlank() -> PlainLyricsView(vmState.lyrics!!)
                else -> Center {
                    NoLyricsWithRefresh(
                        onRefresh = {
                            viewModel.refreshLyrics { _, msg -> snackbar(msg) }
                        }
                    )
                }
            }
        }

        // Botón discreto "Buscar letra de nuevo" cuando SÍ hay letra pero el
        // usuario sospecha que es la equivocada. Lo pintamos al pie con un
        // padding bottom generoso para que no quede pegado al borde del
        // sistema ni oculto tras el inset inferior.
        if (!vmState.lyricsLoading && !vmState.lyrics.isNullOrBlank()) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.Center
            ) {
                TextButton(
                    onClick = {
                        viewModel.refreshLyrics { _, msg -> snackbar(msg) }
                    }
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = null
                    )
                    Spacer(Modifier.height(0.dp))
                    androidx.compose.foundation.layout.Spacer(
                        Modifier.width(6.dp)
                    )
                    Text("Buscar letra de nuevo", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }

}

/**
 * Mensaje "Sin letra disponible" + botón para re-buscar online. Lo usamos
 * en los dos casos vacíos: cuando el fetch inicial falló y cuando el
 * backend devolvió texto vacío.
 */
@Composable
private fun NoLyricsWithRefresh(onRefresh: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "Sin letra disponible",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(12.dp))
        TextButton(onClick = onRefresh) {
            Icon(Icons.Filled.Refresh, contentDescription = null)
            Spacer(Modifier.height(4.dp))
            Text("Buscar letra ahora")
        }
    }
}

/**
 * Letra sincronizada: scroll automático a la línea activa, resaltado en oro,
 * las anteriores/posteriores en gris degradado. Cada línea es CLICABLE —
 * pulsarla hace seek del reproductor a ese momento (como el Musarum antiguo).
 */
@Composable
private fun SyncedLyricsView(
    lines: List<LyricLine>,
    positionMs: Long,
    onSeek: (Long) -> Unit
) {
    val listState = rememberLazyListState()

    val activeIndex by remember(lines, positionMs) {
        derivedStateOf { LrcParser.currentIndex(lines, positionMs) }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            val info = listState.layoutInfo
            // Alineamos la línea activa a ~1/3 del viewport desde arriba
            // (antes iba al centro y quedaba muy baja — más cómodo leer un
            // poco por encima del centro, con contexto hacia adelante).
            val targetOffset = info.viewportSize.height / 3
            listState.animateScrollToItem(
                index = activeIndex.coerceAtLeast(0),
                scrollOffset = -targetOffset.coerceAtLeast(0)
            )
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { Spacer(Modifier.height(140.dp)) }

        itemsIndexed(lines, key = { idx, _ -> idx }) { idx, line ->
            val distance = idx - activeIndex
            LyricRow(
                text = line.text.ifBlank { "♪" },
                distance = distance,
                onClick = { onSeek(line.timeMs) }
            )
        }

        item { Spacer(Modifier.height(220.dp)) }
    }
}

/**
 * Fila de letra sincronizada en modo "karaoke": la línea activa es muy
 * grande (displayMedium, bold, primary); las adyacentes (±1) algo más
 * pequeñas y con alpha; a partir de ±2 se desvanecen más. Las ya pasadas
 * se quedan apagadas en gris claro.
 */
@Composable
private fun LyricRow(
    text: String,
    distance: Int,
    onClick: () -> Unit
) {
    val isActive = distance == 0
    val isAdjacent = distance == -1 || distance == 1
    val hasPassed = distance < 0

    val color by animateColorAsState(
        targetValue = when {
            isActive -> MaterialTheme.colorScheme.primary
            hasPassed -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            isAdjacent -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.9f)
            else -> MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        },
        label = "lyricColor"
    )
    val style = when {
        isActive -> MaterialTheme.typography.displaySmall
        isAdjacent -> MaterialTheme.typography.headlineSmall
        else -> MaterialTheme.typography.titleMedium
    }
    val fontWeight = when {
        isActive -> FontWeight.ExtraBold
        isAdjacent -> FontWeight.SemiBold
        else -> FontWeight.Normal
    }
    Text(
        text = text,
        style = style,
        fontWeight = fontWeight,
        color = color,
        textAlign = TextAlign.Center,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = if (isActive) 10.dp else 4.dp, horizontal = 8.dp)
    )
}

@Composable
private fun PlainLyricsView(text: String) {
    androidx.compose.foundation.layout.Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Progress() {
    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { content() }
}
