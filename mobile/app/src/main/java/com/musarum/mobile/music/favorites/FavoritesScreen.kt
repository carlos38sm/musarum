package com.musarum.mobile.music.favorites

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.data.network.dto.FavoriteDto
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.ui.EmptyView
import com.musarum.mobile.ui.ErrorView
import com.musarum.mobile.ui.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favoritas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "AtrÃ¡s")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            FavoritesContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesContent(
    viewModel: FavoritesViewModel = viewModel(
        factory = FavoritesViewModel.factory(LocalAppContainer.current.favoriteRepository)
    )
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val refreshing by viewModel.refreshing.collectAsStateWithLifecycle()
    val container = LocalAppContainer.current

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        when (val s = state) {
            is FavoritesUiState.Loading -> LoadingView()
            is FavoritesUiState.Error -> ErrorView(
                message = s.message,
                onRetry = { viewModel.refresh() }
            )
            is FavoritesUiState.Ready -> if (s.favorites.isEmpty()) {
                EmptyView(
                    message = "AÃºn no has marcado ninguna canciÃ³n",
                    icon = Icons.Filled.Favorite
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        // Hero YT-Music style: tile grande con icono â™¥
                        // + stats (nÂº pistas, duraciÃ³n total) y debajo la
                        // fila de acciones (Play Â· Shuffle Â· Descargar).
                        // Sustituye a la fila sosa "icono play + nube".
                        val downloads by container.downloadsRepository.entries.collectAsStateWithLifecycle()
                        val inProgress by container.downloadsManager.inProgress.collectAsStateWithLifecycle()
                        val pending = s.favorites.filter {
                            !downloads.containsKey(it.songId) && !inProgress.contains(it.songId)
                        }
                        FavoritesHero(
                            count = s.favorites.size,
                            totalDurationMs = s.favorites.sumOf { (it.durationMs ?: 0).toLong() },
                            allDownloaded = pending.isEmpty(),
                            onPlay = {
                                val songs = s.favorites.map { it.toSongSummary() }
                                container.playerController.playQueue(songs, 0)
                            },
                            onShuffle = {
                                val songs = s.favorites.map { it.toSongSummary() }.shuffled()
                                if (songs.isNotEmpty()) {
                                    container.playerController.playQueue(songs, 0)
                                }
                            },
                            onDownloadAll = {
                                pending.forEach { fav ->
                                    container.downloadsManager.download(fav.toSongSummary())
                                }
                            },
                            downloadEnabled = pending.isNotEmpty()
                        )
                        HorizontalDivider()
                    }

                    items(s.favorites, key = { it.songId }) { fav ->
                        SwipeableFavoriteRow(
                            favorite = fav,
                            onClick = {
                                val songs = s.favorites.map { it.toSongSummary() }
                                container.playerController.playQueue(
                                    songs = songs,
                                    startIndex = s.favorites.indexOf(fav)
                                )
                            },
                            onRemove = { viewModel.toggle(fav.songId) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/**
 * Cabecera estilo YouTube Music para la pantalla de Favoritas: un hero
 * con icono â™¥ grande sobre gradient primary, tÃ­tulo "Favoritas" y
 * subtexto "N pistas Â· X minutos". Debajo, fila de acciones con 3
 * botones redondos (Play Â· Shuffle Â· Descargar). Reemplaza al row sosa
 * que solo tenÃ­a play+nube.
 */
@Composable
private fun FavoritesHero(
    count: Int,
    totalDurationMs: Long,
    allDownloaded: Boolean,
    onPlay: () -> Unit,
    onShuffle: () -> Unit,
    onDownloadAll: () -> Unit,
    downloadEnabled: Boolean
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Hero: cuadro grande con gradient radial del primary al fondo
        // + el corazÃ³n centrado. Mismo lenguaje visual que los tiles de
        // Biblioteca (Favoritas usa primary).
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(180.dp)
                .clip(androidx.compose.foundation.shape.RoundedCornerShape(20.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(96.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "Favoritas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            Text(
                favoritesStatsLine(count, totalDurationMs),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        // Fila de acciones: play grande (primary), shuffle y descargar
        // como botones secundarios. SeparaciÃ³n 12dp cÃ³moda para el pulgar.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledIconButton(
                onClick = onPlay,
                modifier = Modifier.size(52.dp)
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir favoritas",
                    modifier = Modifier.size(28.dp)
                )
            }
            Surface(
                modifier = Modifier.size(48.dp).clickable(onClick = onShuffle),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Shuffle,
                        contentDescription = "Reproducir aleatorio",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(
                enabled = downloadEnabled,
                onClick = onDownloadAll
            ) {
                Icon(
                    imageVector = if (allDownloaded) Icons.Filled.CloudDone
                                  else Icons.Filled.CloudDownload,
                    contentDescription = if (allDownloaded) "Todas descargadas"
                                         else "Descargar favoritas",
                    tint = if (downloadEnabled) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** "N pistas Â· X min" o "N pistas Â· 1 h 20 min" segÃºn duraciÃ³n total. */
private fun favoritesStatsLine(count: Int, totalMs: Long): String {
    val pieces = StringBuilder()
    pieces.append(if (count == 1) "1 pista" else "$count pistas")
    if (totalMs > 0) {
        val totalMin = (totalMs / 60_000L).toInt()
        pieces.append(" Â· ")
        if (totalMin < 60) {
            pieces.append("$totalMin min")
        } else {
            val h = totalMin / 60
            val m = totalMin % 60
            pieces.append(if (m == 0) "$h h" else "$h h $m min")
        }
    }
    return pieces.toString()
}

/**
 * FavoriteRow envuelto en SwipeToDismissBox: un swipe a cualquier lado
 * ejecuta onRemove. Pedido Carlos nov-24 2026 â€” asÃ­ el â™¥ de cada canciÃ³n
 * ya no es necesario, y la lista queda mÃ¡s limpia.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableFavoriteRow(
    favorite: FavoriteDto,
    onClick: () -> Unit,
    onRemove: () -> Unit
) {
    val dismissState = androidx.compose.material3.rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd ||
                value == androidx.compose.material3.SwipeToDismissBoxValue.EndToStart
            ) {
                onRemove()
                true
            } else false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.5f }
    )
    androidx.compose.material3.SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            val alignment = when (dismissState.dismissDirection) {
                androidx.compose.material3.SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                androidx.compose.material3.SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            // Fondo rellena toda la fila (alto+ancho) y pinta papelera en
            // ambos lados â€” cualquier swipe elimina la favorita. El icono
            // corazÃ³n de antes confundÃ­a (parecÃ­a "aÃ±adir" en vez de
            // "quitar"). Pedido Carlos nov-24-pm.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.error)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onError
                )
            }
        },
        // Ambos lados: eliminar. En Favoritas no hay otra acciÃ³n vÃ¡lida
        // (no se "aÃ±ade" lo que ya es favorito); dos direcciones quitando
        // es menos cargado que meter algo arbitrario en uno de ellos.
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        FavoriteRowContent(favorite = favorite, onClick = onClick)
    }
}

@Composable
private fun FavoriteRowContent(
    favorite: FavoriteDto,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(
            url = favorite.coverUrl,
            size = 48.dp,
            cornerRadius = 6.dp,
            placeholderKind = com.musarum.mobile.music.PlaceholderKind.SONGS,
            placeholderSeed = favorite.songId
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                favorite.songTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                append(favorite.artists.joinToString(", ") { it.name })
                if (!favorite.albumTitle.isNullOrBlank()) {
                    if (isNotEmpty()) append(" â€¢ ")
                    append(favorite.albumTitle)
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        // â™¥ eliminado â€” el swipe lateral lo sustituye.
    }
}
