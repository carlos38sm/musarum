package com.musarum.mobile.music.library

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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.data.downloads.DownloadInfo
import com.musarum.mobile.data.network.dto.ArtistRefDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.music.CoverImage

/**
 * Lista de canciones descargadas localmente. Pinta directamente desde el
 * {@link com.musarum.mobile.data.downloads.DownloadsRepository} sin tocar
 * al backend — por eso funciona offline. Rediseño nov-24 2026:
 *  - Hero estilo YT Music (igual que Favoritas) con icono CloudDone grande
 *    sobre gradient tertiary + stats.
 *  - Botones Play/Shuffle en la fila de acciones, solo iconos.
 *  - Papelera global a la derecha de la cabecera (borra todas con confirm).
 *  - Cada canción pinta su propia portada (como SongRow) con badge esquina
 *    señalando si es temporal (reloj) o permanente (check).
 *  - Swipe lateral sobre una canción la elimina de descargas (se mantiene
 *    en la biblioteca online).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val entries by container.downloadsRepository.entries.collectAsState()
    val list = entries.values.sortedByDescending { it.downloadedAt }

    var confirmClearAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Descargadas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    // Papelera global (borrar todas) — pedida Carlos nov-24.
                    // Solo visible si hay algo que borrar; evita acción vacía.
                    if (list.isNotEmpty()) {
                        IconButton(onClick = { confirmClearAll = true }) {
                            Icon(
                                Icons.Filled.DeleteSweep,
                                contentDescription = "Borrar todas las descargas",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
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
                        DownloadsHero(
                            count = list.size,
                            totalBytes = list.sumOf { it.sizeBytes },
                            onPlay = {
                                container.playerController.playQueue(
                                    songs = list.map { it.toSongSummary() },
                                    startIndex = 0
                                )
                            },
                            onShuffle = {
                                val shuffled = list.map { it.toSongSummary() }.shuffled()
                                if (shuffled.isNotEmpty()) {
                                    container.playerController.playQueue(shuffled, 0)
                                }
                            }
                        )
                        HorizontalDivider()
                    }
                    items(list, key = { it.songId }) { info ->
                        SwipeableDownloadRow(
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
                            }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { confirmClearAll = false },
            icon = {
                Icon(
                    Icons.Filled.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error
                )
            },
            title = { Text("Borrar todas las descargas") },
            text = {
                Text(
                    "Se eliminarán ${list.size} descargas de este dispositivo. " +
                    "Las canciones siguen en la biblioteca online, solo dejan " +
                    "de estar offline."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearAll = false
                    container.downloadsManager.clearAll()
                    snackbar("Descargas eliminadas")
                }) {
                    Text("Borrar", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearAll = false }) { Text("Cancelar") }
            }
        )
    }
}

/**
 * Hero + fila de acciones para la pantalla de Descargas. Clon visual del
 * de Favoritas pero con color tertiary y icono CloudDone para no pelear
 * por el mismo tono.
 */
@Composable
private fun DownloadsHero(
    count: Int,
    totalBytes: Long,
    onPlay: () -> Unit,
    onShuffle: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .height(180.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.18f)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.CloudDone,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onTertiary,
                modifier = Modifier.size(96.dp)
            )
        }
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            Text(
                "Descargadas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(2.dp))
            val mb = totalBytes / (1024.0 * 1024.0)
            Text(
                when (count) {
                    1 -> "1 pista · ${"%.1f".format(mb)} MB"
                    else -> "$count pistas · ${"%.1f".format(mb)} MB"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FilledIconButton(
                onClick = onPlay,
                modifier = Modifier.size(52.dp),
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiary,
                    contentColor = MaterialTheme.colorScheme.onTertiary
                )
            ) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir todas",
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
        }
    }
}

/**
 * Fila con swipe-to-dismiss sobre DownloadRow. Un swipe en cualquier
 * dirección elimina la descarga. Al ejecutar onRemove el item sale de la
 * lista (DownloadsRepository emite un entries nuevo sin esa key) y por
 * tanto desaparece sin necesidad de animar el collapse a mano.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableDownloadRow(
    info: DownloadInfo,
    onPlay: () -> Unit,
    onRemove: () -> Unit
) {
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.StartToEnd ||
                value == SwipeToDismissBoxValue.EndToStart
            ) {
                onRemove()
                true
            } else false
        },
        positionalThreshold = { totalDistance -> totalDistance * 0.5f }
    )
    // Reseteo defensivo: si el item vuelve a pintarse (p. ej. re-descarga)
    // el estado del swipe debe estar en Settled.
    LaunchedEffect(info.songId) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }
    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            // Fondo rojo con icono papelera a los dos lados — al deslizar
            // el usuario ve claro "esto se va a borrar".
            val alignment = when (dismissState.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> Alignment.CenterStart
                SwipeToDismissBoxValue.EndToStart -> Alignment.CenterEnd
                else -> Alignment.Center
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.errorContainer)
                    .padding(horizontal = 24.dp),
                contentAlignment = alignment
            ) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer
                )
            }
        },
        enableDismissFromStartToEnd = true,
        enableDismissFromEndToStart = true
    ) {
        DownloadRow(info = info, onPlay = onPlay)
    }
}

@Composable
private fun DownloadRow(
    info: DownloadInfo,
    onPlay: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onPlay)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Portada de la canción + badge esquina sup derecha señalando
        // modo (reloj = temporal, check = permanente). Idea pedida por
        // Carlos nov-24: "como una notificación, un puntito arriba".
        // DownloadInfo no guarda coverUrl, así que usamos el placeholder
        // determinista por songId (mismo que SongRow cuando no hay cover).
        Box(modifier = Modifier.size(48.dp)) {
            CoverImage(
                url = null,
                size = 48.dp,
                cornerRadius = 8.dp,
                placeholderKind = com.musarum.mobile.music.PlaceholderKind.SONGS,
                placeholderSeed = info.songId
            )
            // Badge 16dp arriba a la derecha.
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .offset(x = (48 - 14).dp, y = (-2).dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (info.isTemporary) Icons.Filled.Schedule
                                  else Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = if (info.isTemporary) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.size(16.dp)
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
        // La basura fila-por-fila se ha retirado nov-24 2026: el swipe
        // lateral se encarga, y la papelera global vive arriba a la dcha.
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
