package com.musarum.mobile.music.albums

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.music.SongActionsHost
import com.musarum.mobile.music.SongRow

@Composable
fun AlbumDetailScreen(
    albumId: Long,
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: AlbumDetailViewModel = viewModel(
        key = "album_$albumId",
        factory = AlbumDetailViewModel.factory(container.albumRepository, albumId)
    )
    val state by viewModel.state.collectAsState()

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SongActionsHost { openSongActions ->
        when (val s = state) {
            is AlbumDetailUiState.Loading -> Box(
                Modifier.fillMaxSize(), Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

            is AlbumDetailUiState.Error -> Box(
                Modifier.fillMaxSize(), Alignment.Center
            ) { Text(s.message, color = MaterialTheme.colorScheme.error) }

            is AlbumDetailUiState.Ready -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                item {
                    AlbumHero(
                        coverUrl = s.album.coverUrl,
                        title = s.album.title,
                        artistName = s.album.artistName,
                        artistId = s.album.artistId,
                        releaseYear = s.album.releaseYear,
                        type = s.album.inferredType ?: s.album.type,
                        songCount = s.album.songCount,
                        totalDurationMs = s.album.totalDurationMs,
                        onBack = onBack,
                        onOpenArtist = onOpenArtist,
                        onPlay = {
                            if (s.songs.isNotEmpty()) container.playerController.playQueue(s.songs, 0)
                        },
                        onShuffle = {
                            if (s.songs.isNotEmpty()) {
                                container.playerController.playQueue(s.songs.shuffled(), 0)
                            }
                        }
                    )
                }

                itemsIndexed(s.songs) { index, song ->
                    SongRow(
                        song = song,
                        onClick = {
                            container.playerController.playQueue(
                                songs = s.songs,
                                startIndex = index
                            )
                        },
                        onLongPress = { openSongActions(song) }
                    )
                }
                item { Spacer(Modifier.height(100.dp)) }
            }
        }
        }
    }
}

@Composable
private fun AlbumHero(
    coverUrl: String?,
    title: String,
    artistName: String?,
    artistId: Long?,
    releaseYear: Int?,
    type: String?,
    songCount: Long,
    totalDurationMs: Long,
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(380.dp)
    ) {
        // Fondo difuminado con la portada
        if (!coverUrl.isNullOrBlank()) {
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(50.dp)
            )
        }
        // Gradiente oscuro sobre el blur
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.4f),
                        0.6f to MaterialTheme.colorScheme.background.copy(alpha = 0.75f),
                        1.0f to MaterialTheme.colorScheme.background
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp, start = 16.dp, end = 16.dp, bottom = 16.dp)
                .padding(
                    top = androidx.compose.foundation.layout.WindowInsets.statusBars
                        .asPaddingValues().calculateTopPadding()
                )
        ) {
            // Botón atrás flotante
            IconButton(
                onClick = onBack,
                modifier = Modifier.size(42.dp)
            ) {
                Icon(
                    Icons.Filled.ArrowBack,
                    contentDescription = "Atrás",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Spacer(Modifier.weight(1f))

            // Cover centrado grande
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(170.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 16.dp
                ) {
                    CoverImage(url = coverUrl, size = 170.dp, cornerRadius = 16.dp)
                }
            }

            Spacer(Modifier.height(18.dp))

            // Metadatos
            Text(
                title,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            artistName?.let { name ->
                val mod = if (artistId != null) Modifier.clickable { onOpenArtist(artistId) } else Modifier
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp).then(mod)
                )
            }
            val meta = buildList {
                type?.let { add(it) }
                releaseYear?.let { add(it.toString()) }
                if (songCount > 0) {
                    add("$songCount ${if (songCount == 1L) "canción" else "canciones"}")
                }
                if (totalDurationMs > 0) add(formatAlbumMinutes(totalDurationMs))
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            // Botones de acción tipo pill (foto 5)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                PillAction(
                    icon = Icons.Filled.PlayArrow,
                    label = "Reproducir",
                    primary = true,
                    onClick = onPlay
                )
                PillAction(
                    icon = Icons.Filled.Shuffle,
                    label = "Aleatorio",
                    primary = false,
                    onClick = onShuffle
                )
            }
        }
    }
}

private fun formatAlbumMinutes(ms: Long): String {
    val totalMin = (ms / 60_000).toInt()
    if (totalMin < 60) return "$totalMin min"
    val h = totalMin / 60
    val m = totalMin % 60
    return if (m == 0) "$h h" else "$h h $m min"
}

@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun PillAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    primary: Boolean,
    onClick: () -> Unit
) {
    // Pill robusto: altura mínima explícita + RoundedCornerShape 50% (pastilla
    // real, no CircleShape que a veces colapsa el contenido en BOM recientes).
    val bg = if (primary) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(50),
        color = bg,
        contentColor = fg,
        modifier = Modifier.height(40.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(label, style = MaterialTheme.typography.labelLarge)
        }
    }
}
