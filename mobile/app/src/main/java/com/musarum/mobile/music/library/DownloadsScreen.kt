package com.musarum.mobile.music.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.data.downloads.DownloadInfo
import com.musarum.mobile.data.network.dto.ArtistRefDto
import com.musarum.mobile.data.network.dto.SongSummaryDto

/**
 * Lista de canciones descargadas localmente. Pinta directamente desde el
 * {@link com.musarum.mobile.data.downloads.DownloadsRepository} sin tocar
 * al backend — por eso funciona offline. Cada fila distingue permanente de
 * temporal con un icono distinto a la derecha.
 *
 * Acciones por canción: reproducir (toda la cola desde esa posición) o
 * borrar la descarga. Al borrar NO se toca el servidor: la canción sigue en
 * la biblioteca, solo deja de estar offline.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val entries by container.downloadsRepository.entries.collectAsState()
    val list = entries.values.sortedByDescending { it.downloadedAt }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Descargadas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (list.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(Modifier.size(12.dp))
                    Text(
                        "No tienes canciones descargadas",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(4.dp))
                    Text(
                        "Pulsa el icono de descarga en el reproductor para guardar canciones offline",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 32.dp)
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Column(modifier = Modifier.padding(16.dp)) {
                            val totalMb = list.sumOf { it.sizeBytes } / (1024.0 * 1024.0)
                            Text(
                                "${list.size} canciones · ${"%.1f".format(totalMb)} MB",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.size(12.dp))
                            Button(onClick = {
                                container.playerController.playQueue(
                                    songs = list.map { it.toSongSummary() },
                                    startIndex = 0
                                )
                            }) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Reproducir todo")
                            }
                        }
                        HorizontalDivider()
                    }
                    items(list, key = { it.songId }) { info ->
                        DownloadRow(
                            info = info,
                            onPlay = {
                                val idx = list.indexOf(info).coerceAtLeast(0)
                                container.playerController.playQueue(
                                    songs = list.map { it.toSongSummary() },
                                    startIndex = idx
                                )
                            },
                            onRemove = {
                                container.downloadsManager.remove(info.songId)
                                snackbar("Descarga eliminada")
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    info: DownloadInfo,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Icono de estado — permanente vs temporal — en lugar del cover, que
        // aquí no aporta: ya se intuye que son todas descargadas.
        Surface(
            modifier = Modifier.size(44.dp),
            shape = CircleShape,
            color = if (info.isTemporary) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    else MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (info.isTemporary) Icons.Filled.Schedule
                                  else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (info.isTemporary) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                info.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val mb = info.sizeBytes / (1024.0 * 1024.0)
            Text(
                buildString {
                    append(info.artistsLabel)
                    append(" · ")
                    append("%.1f MB".format(mb))
                    if (info.isTemporary) append(" · temporal")
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Borrar descarga",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun DownloadInfo.toSongSummary(): SongSummaryDto = SongSummaryDto(
    id = songId,
    title = title,
    durationMs = null,
    trackNumber = null,
    artists = listOf(ArtistRefDto(id = 0, name = artistsLabel)),
    coverUrl = null
)
