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
import androidx.compose.runtime.collectAsState
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
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
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
    val state by viewModel.state.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
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
                    message = "Aún no has marcado ninguna canción",
                    icon = Icons.Filled.Favorite
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        // Hero YT-Music style: tile grande con icono ♥
                        // + stats (nº pistas, duración total) y debajo la
                        // fila de acciones (Play · Shuffle · Descargar).
                        // Sustituye a la fila sosa "icono play + nube".
                        val downloads by container.downloadsRepository.entries.collectAsState()
                        val inProgress by container.downloadsManager.inProgress.collectAsState()
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
                        FavoriteRow(
                            favorite = fav,
                            onClick = {
                                val songs = s.favorites.map { it.toSongSummary() }
                                container.playerController.playQueue(
                                    songs = songs,
                                    startIndex = s.favorites.indexOf(fav)
                                )
                            },
                            onToggle = { viewModel.toggle(fav.songId) }
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
 * con icono ♥ grande sobre gradient primary, título "Favoritas" y
 * subtexto "N pistas · X minutos". Debajo, fila de acciones con 3
 * botones redondos (Play · Shuffle · Descargar). Reemplaza al row sosa
 * que solo tenía play+nube.
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
        // + el corazón centrado. Mismo lenguaje visual que los tiles de
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
        // como botones secundarios. Separación 12dp cómoda para el pulgar.
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

/** "N pistas · X min" o "N pistas · 1 h 20 min" según duración total. */
private fun favoritesStatsLine(count: Int, totalMs: Long): String {
    val pieces = StringBuilder()
    pieces.append(if (count == 1) "1 pista" else "$count pistas")
    if (totalMs > 0) {
        val totalMin = (totalMs / 60_000L).toInt()
        pieces.append(" · ")
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

@Composable
private fun FavoriteRow(
    favorite: FavoriteDto,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(url = favorite.coverUrl, size = 48.dp, cornerRadius = 6.dp)
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
                    if (isNotEmpty()) append(" • ")
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
        IconButton(onClick = onToggle) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "Quitar de favoritas",
                tint = com.musarum.mobile.ui.FavoriteRed
            )
        }
    }
}
