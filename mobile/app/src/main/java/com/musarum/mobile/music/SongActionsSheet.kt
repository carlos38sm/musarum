package com.musarum.mobile.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PlaylistAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.data.network.dto.SongSummaryDto

/**
 * BottomSheet que aparece al mantener pulsada una canción. Siempre ofrece
 * "Añadir a playlist"; si el usuario actual es OWNER, además añade la
 * opción "Borrar canción" (fila en rojo). La acción de borrar la lanza el
 * caller tras confirmación en un diálogo aparte — este sheet solo recoge
 * la intención.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SongActionsSheet(
    song: SongSummaryDto,
    isOwner: Boolean,
    onDismiss: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onRequestDelete: () -> Unit
) {
    val container = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val entries by container.downloadsRepository.entries.collectAsState()
    val inProgress by container.downloadsManager.inProgress.collectAsState()
    val entry = entries[song.id]
    val isDownloading = inProgress.contains(song.id)
    val favoriteIds by container.favoriteRepository.favoriteIds.collectAsState()
    val isFavorite = song.id in favoriteIds

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            // Cabecera: título + artista (compacta).
            Column(modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) {
                Text(
                    song.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    song.artistsLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(8.dp))

            // Me gusta: primera acción porque es lo que el usuario pide más.
            // Antes para marcar favorito había que entrar a NowPlaying e
            // interrumpir lo que estuviera sonando. Ahora long-press →
            // toggle directo sin salir del contexto.
            SheetAction(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (isFavorite) "Quitar de favoritas" else "Me gusta",
                iconTint = if (isFavorite) MaterialTheme.colorScheme.primary else null,
                onClick = {
                    scope.launch {
                        runCatching { container.favoriteRepository.toggle(song.id) }
                    }
                    onDismiss()
                }
            )

            SheetAction(
                icon = Icons.Filled.PlaylistAdd,
                label = "Añadir a playlist",
                onClick = onAddToPlaylist
            )

            // ── Acciones de descarga offline ───────────────────────────────
            when {
                isDownloading -> SheetAction(
                    icon = Icons.Filled.Downloading,
                    label = "Descargando…",
                    onClick = {} // sin acción mientras va
                )
                entry == null -> SheetAction(
                    icon = Icons.Filled.CloudDownload,
                    label = "Descargar",
                    onClick = {
                        container.downloadsManager.download(song)
                        val temp = container.playerPreferences.downloadsTemporaryByDefault.value
                        snackbar(if (temp) "Descargando (temporal 24h)" else "Descargando (permanente)")
                        onDismiss()
                    }
                )
                entry.isTemporary -> {
                    SheetAction(
                        icon = Icons.Filled.CheckCircle,
                        label = "Mantener permanente",
                        onClick = {
                            container.downloadsManager.setKeepPermanent(song.id, true)
                            snackbar("Descarga marcada como permanente")
                            onDismiss()
                        }
                    )
                    SheetAction(
                        icon = Icons.Filled.RemoveCircleOutline,
                        label = "Borrar descarga",
                        onClick = {
                            container.downloadsManager.remove(song.id)
                            snackbar("Descarga eliminada")
                            onDismiss()
                        }
                    )
                }
                else -> SheetAction(
                    icon = Icons.Filled.DownloadDone,
                    label = "Borrar descarga",
                    onClick = {
                        container.downloadsManager.remove(song.id)
                        snackbar("Descarga eliminada")
                        onDismiss()
                    }
                )
            }

            if (isOwner) {
                SheetAction(
                    icon = Icons.Filled.Delete,
                    label = "Borrar canción",
                    destructive = true,
                    onClick = onRequestDelete
                )
            }
        }
    }
}

@Composable
private fun SheetAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    destructive: Boolean = false,
    iconTint: Color? = null,
    onClick: () -> Unit
) {
    val labelColor = if (destructive) MaterialTheme.colorScheme.error
    else MaterialTheme.colorScheme.onSurface
    val resolvedIconTint = iconTint ?: labelColor
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = resolvedIconTint, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(16.dp))
        Text(
            label,
            style = MaterialTheme.typography.bodyLarge,
            color = labelColor
        )
    }
}

/**
 * Helper reutilizable: gestiona el estado del sheet de acciones + los
 * diálogos derivados (añadir a playlist, confirmar borrado) en un solo
 * sitio. Las pantallas de lista solo tienen que llamar a `open(song)` en
 * el long-press y poner `SongActionsHost(...)` en el árbol UI.
 *
 * - isOwner se consulta del container (me) para pintar la opción de
 *   "Borrar canción" solo si procede.
 * - onDelete por defecto llama a songRepository.delete(id) con snackbar.
 *   Las pantallas que necesiten un reset local (p. ej. SongsScreen
 *   quitando la fila al instante) pueden sobrescribirlo.
 */
@Composable
fun SongActionsHost(
    content: @Composable (open: (SongSummaryDto) -> Unit) -> Unit
) {
    val container = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val me by container.userRepository.me.collectAsState()
    val isOwner = me?.userRole == "OWNER"

    var sheetSong by remember { mutableStateOf<SongSummaryDto?>(null) }
    var addToPlaylistSongId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteSong by remember { mutableStateOf<SongSummaryDto?>(null) }

    content { song -> sheetSong = song }

    sheetSong?.let { song ->
        SongActionsSheet(
            song = song,
            isOwner = isOwner,
            onDismiss = { sheetSong = null },
            onAddToPlaylist = {
                addToPlaylistSongId = song.id
                sheetSong = null
            },
            onRequestDelete = {
                confirmDeleteSong = song
                sheetSong = null
            }
        )
    }

    addToPlaylistSongId?.let { id ->
        com.musarum.mobile.music.library.AddToPlaylistDialog(
            songId = id,
            onDismiss = { addToPlaylistSongId = null }
        )
    }

    confirmDeleteSong?.let { song ->
        ConfirmDeleteSongDialog(
            song = song,
            onDismiss = { confirmDeleteSong = null },
            onConfirm = {
                val sid = song.id
                confirmDeleteSong = null
                scope.launch {
                    runCatching { container.songRepository.delete(sid) }
                        .onSuccess { snackbar("Canción eliminada") }
                        .onFailure { snackbar("No se pudo borrar: ${it.message ?: "error"}") }
                }
            }
        )
    }
}

/**
 * Confirmación destructiva. "Borrar" en rojo; "Cancelar" por defecto.
 * El caller resuelve la acción tras el yes.
 */
@Composable
fun ConfirmDeleteSongDialog(
    song: SongSummaryDto,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("¿Borrar \"${song.title}\"?") },
        text = {
            Text(
                "Se eliminará la canción del servidor Y su archivo del disco. " +
                    "Los favoritos y entradas en playlists de todos los usuarios " +
                    "también se perderán. No se puede deshacer.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Borrar", color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}
