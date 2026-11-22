package com.musarum.mobile.music.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.foundation.combinedClickable
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.data.network.dto.PlaylistSummaryDto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(
    onOpenPlaylist: (Long) -> Unit,
    onOpenFavorites: () -> Unit,
    onOpenDownloads: () -> Unit,
    playlistsViewModel: PlaylistsViewModel = viewModel(
        factory = PlaylistsViewModel.factory(LocalAppContainer.current.playlistRepository)
    )
) {
    val container = LocalAppContainer.current
    val favorites by container.favoriteRepository.favorites.collectAsState()
    val downloads by container.downloadsRepository.entries.collectAsState()
    val snackbar = LocalSnackbar.current
    val playlistsState by playlistsViewModel.state.collectAsState()
    val shared by playlistsViewModel.shared.collectAsState()

    var showCreate by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PlaylistSummaryDto?>(null) }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Biblioteca") }) },
        floatingActionButton = {
            // FAB flotante redondo pleno (CircleShape) — por defecto Material3
            // le da forma squircle, aquí lo forzamos a círculo porque el
            // usuario lo prefiere así. Crear playlist sin invadir el header.
            FloatingActionButton(
                onClick = { showCreate = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Nueva playlist")
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // ── Fijadas: dos tarjetas lado a lado (descargas · favoritas) ──
            item { SectionLabel("Fijadas") }
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    DownloadsTile(
                        count = downloads.size,
                        totalBytes = downloads.values.sumOf { it.sizeBytes },
                        onClick = onOpenDownloads,
                        modifier = Modifier.weight(1f)
                    )
                    FavoritesTile(
                        count = favorites.size,
                        onClick = onOpenFavorites,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            when (val s = playlistsState) {
                is PlaylistsUiState.Loading -> item {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) { CircularProgressIndicator() }
                }
                is PlaylistsUiState.Error -> item {
                    Text(
                        s.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
                is PlaylistsUiState.Ready -> {
                    // Antes repartíamos las propias en "Colaborativas" (las
                    // isPublic=true) + "Propias" (las privadas). Era un
                    // mapeo falso: isPublic significa "visible por ID" y
                    // "colaborativa" significa "tiene colaboradores". Ahora
                    // las secciones son por propiedad: "Mis playlists" (las
                    // que creé yo) + "Compartidas conmigo" (donde otro
                    // user me invitó como colaborador).
                    val own = s.playlists

                    item { SectionLabel("Mis playlists") }
                    if (own.isEmpty()) {
                        item {
                            Text(
                                "Aún no tienes playlists propias — crea una con el botón +.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                            )
                        }
                    } else {
                        items(own, key = { "own_${it.id}" }) { pl ->
                            PlaylistRow(
                                playlist = pl,
                                onClick = { onOpenPlaylist(pl.id) },
                                onDelete = { pendingDelete = pl }
                            )
                        }
                    }

                    // Solo pintamos la sección si hay algo: ahorra espacio
                    // vertical cuando el usuario no participa en ninguna
                    // playlist ajena (lo normal al principio).
                    if (shared.isNotEmpty()) {
                        item { SectionLabel("Compartidas conmigo") }
                        items(shared, key = { "shared_${it.id}" }) { pl ->
                            PlaylistRow(
                                playlist = pl,
                                onClick = { onOpenPlaylist(pl.id) },
                                // No le dejamos borrar: no es suya. El
                                // "salir" de la playlist lo hará el owner
                                // quitándole como colaborador.
                                onDelete = null
                            )
                        }
                    }
                }
            }
        }
    }

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name, desc ->
                playlistsViewModel.create(name, desc)
                snackbar("Playlist \"$name\" creada")
                showCreate = false
            }
        )
    }

    pendingDelete?.let { pl ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Eliminar playlist") },
            text = { Text("¿Seguro que quieres eliminar \"${pl.name}\"?") },
            confirmButton = {
                TextButton(onClick = {
                    playlistsViewModel.delete(pl.id)
                    snackbar("Playlist eliminada")
                    pendingDelete = null
                }) { Text("Eliminar") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 4.dp)
    )
}

@Composable
private fun FavoritesEntry(count: Int, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Favoritas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    when (count) {
                        0 -> "Marca canciones con el ♥ para añadirlas"
                        1 -> "1 canción"
                        else -> "$count canciones"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Tarjeta cuadrada "Descargadas" en la fila de Fijadas (dos columnas).
 * Layout vertical: icono grande arriba, título + subtexto abajo. Color
 * tertiary para diferenciarse de Favoritas (primary).
 */
@Composable
private fun DownloadsTile(
    count: Int,
    totalBytes: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.tertiary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                "Descargadas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            val mb = totalBytes / (1024.0 * 1024.0)
            Text(
                when (count) {
                    0 -> "Nada aún"
                    1 -> "1 pista · ${"%.1f".format(mb)} MB"
                    else -> "$count pistas · ${"%.1f".format(mb)} MB"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Tarjeta cuadrada "Favoritas" en la fila de Fijadas (dos columnas). Color
 * primary. Mismo layout que DownloadsTile.
 */
@Composable
private fun FavoritesTile(
    count: Int,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
            Text(
                "Favoritas",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                when (count) {
                    0 -> "Nada aún"
                    1 -> "1 pista"
                    else -> "$count pistas"
                },
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

/**
 * Entrada "Descargadas" en la sección Fijadas de Biblioteca. Mismo patrón
 * visual que Favoritas pero con color tertiary para distinguirlas y evitar
 * que ambas peleen por el mismo tono primary.
 */
@Composable
private fun DownloadsEntry(count: Int, totalBytes: Long, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.tertiary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.CloudDone,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onTertiary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Descargadas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                val mb = totalBytes / (1024.0 * 1024.0)
                Text(
                    when (count) {
                        0 -> "Toca el icono de descarga para guardarlas offline"
                        1 -> "1 canción · ${"%.1f".format(mb)} MB"
                        else -> "$count canciones · ${"%.1f".format(mb)} MB"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Tarjeta pálida que anuncia la feature de playlists colaborativas (varias
 * personas editando la misma lista — pensada para viajes compartidos). Se
 * dibuja apagada hasta que la implementemos.
 */
@Composable
private fun CollaborativePlaceholder() {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.18f)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Filled.GroupAdd,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Playlists compartidas",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    "Próximamente — edita listas con otras personas",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun PlaylistRow(
    playlist: PlaylistSummaryDto,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    // Tap = abrir playlist; long-press = confirmación de borrado (si es
    // "mía"). Antes había un icono "papelera" flotando a la derecha que
    // ensuciaba la fila — Carlos prefiere limpio. El long-press es el
    // mismo gesto que SongRow usa para sus acciones avanzadas. Para las
    // playlists compartidas conmigo pasamos onDelete=null: solo tap, no
    // borro algo que no es mío.
    val rowModifier = Modifier
        .fillMaxWidth()
        .let { base ->
            if (onDelete != null) base.combinedClickable(onClick = onClick, onLongClick = onDelete)
            else base.clickable(onClick = onClick)
        }
        .padding(horizontal = 16.dp, vertical = 10.dp)
    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Mosaico 2×2 con las portadas de las 4 primeras canciones
        // (bloque B20). Los huecos se rellenan con placeholder de
        // playlists — si _placeholders/playlists está vacío, cae a gris.
        com.musarum.mobile.music.PlaylistMosaicCover(
            urls = playlist.coverUrls,
            playlistId = playlist.id,
            size = 52.dp,
            cornerRadius = 8.dp
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                playlist.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "${playlist.songCount} canciones",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
