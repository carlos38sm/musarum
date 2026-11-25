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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.CloudDone
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
    val entries by container.downloadsRepository.entries.collectAsStateWithLifecycle()
    val inProgress by container.downloadsManager.inProgress.collectAsStateWithLifecycle()
    val entry = entries[song.id]
    val isDownloading = inProgress.contains(song.id)
    val favoriteIds by container.favoriteRepository.favoriteIds.collectAsStateWithLifecycle()
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
            // interrumpir lo que estuviera sonando. Ahora long-press â†’
            // toggle directo sin salir del contexto.
            SheetAction(
                icon = if (isFavorite) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
                label = if (isFavorite) "Quitar de favoritas" else "Me gusta",
                iconTint = if (isFavorite) com.musarum.mobile.ui.FavoriteRed else null,
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

            // "Enviar a..." — abre un sub-sheet con los otros users de la red
            // para que el OWNER (o cualquier user) comparta esta canción con
            // alguien. Cada envío se guarda en la bandeja del destinatario.
            var showSendTo by remember { mutableStateOf(false) }
            SheetAction(
                icon = Icons.AutoMirrored.Filled.Send,
                label = "Enviar a…",
                onClick = { showSendTo = true }
            )
            if (showSendTo) {
                SendToUserSheet(
                    song = song,
                    onDismiss = { showSendTo = false; onDismiss() }
                )
            }

            // Si hay una fiesta activa, permite añadir esta canción a la
            // cola compartida. La detectamos en caliente: un LaunchedEffect
            // ligero consulta /party/active al abrir el sheet y guarda el id.
            var activePartyId by remember { mutableStateOf<Long?>(null) }
            androidx.compose.runtime.LaunchedEffect(song.id) {
                val resp = runCatching { container.apiService.getActiveParty() }.getOrNull()
                activePartyId = if (resp != null && resp.isSuccessful) resp.body()?.id else null
            }
            activePartyId?.let { pid ->
                SheetAction(
                    icon = androidx.compose.material.icons.Icons.Filled.Celebration,
                    label = "Añadir a la fiesta",
                    onClick = {
                        scope.launch {
                            runCatching {
                                container.apiService.addSongToParty(
                                    pid,
                                    com.musarum.mobile.data.network.dto.AddPartySongRequest(song.id)
                                )
                            }.onSuccess { snackbar("Añadida a la fiesta") }
                                .onFailure { snackbar("No se pudo añadir a la fiesta") }
                        }
                        onDismiss()
                    }
                )
            }

            // â”€â”€ Acciones de descarga offline â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€
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
                        // Sin snackbar: el badge de descarga en la portada
                        // aparece al instante (spinner â†’ reloj/check).
                        onDismiss()
                    }
                )
                entry.isTemporary -> {
                    SheetAction(
                        icon = Icons.Filled.CheckCircle,
                        label = "Mantener permanente",
                        onClick = {
                            container.downloadsManager.setKeepPermanent(song.id, true)
                            onDismiss()
                        }
                    )
                    SheetAction(
                        icon = Icons.Filled.RemoveCircleOutline,
                        label = "Borrar descarga",
                        onClick = {
                            container.downloadsManager.remove(song.id)
                            onDismiss()
                        }
                    )
                }
                else -> SheetAction(
                    icon = Icons.Filled.CloudDone,
                    label = "Borrar descarga",
                    onClick = {
                        container.downloadsManager.remove(song.id)
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
 * Sub-sheet "Enviar a..." — lista los otros users de la red (usa el mismo
 * endpoint /auth/users que el picker Netflix del Login) y envía la canción
 * al tocar uno. Sin confirmación extra: un tap, un envío. El destinatario
 * la verá en su Bandeja al abrir Musarum.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SendToUserSheet(
    song: SongSummaryDto,
    onDismiss: () -> Unit
) {
    val container = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val me by container.userRepository.me.collectAsStateWithLifecycle()
    var users by remember {
        mutableStateOf<List<com.musarum.mobile.data.network.dto.LoginUserDto>>(emptyList())
    }
    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { container.apiService.getLoginUsers() }
            .onSuccess { fetched ->
                users = fetched.filter { it.id != me?.id }
            }
    }

    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)) {
            Text(
                "Enviar \"${song.title}\" a…",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
            )
            if (users.isEmpty()) {
                Text(
                    "No hay otros usuarios en la red.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
                )
            } else {
                users.forEach { u ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                scope.launch {
                                    val req = com.musarum.mobile.data.network.dto.SendShareRequest(
                                        toUserId = u.id,
                                        songId = song.id
                                    )
                                    runCatching { container.apiService.sendInbox(req) }
                                        .onSuccess { snackbar("Enviado a ${u.nick}") }
                                        .onFailure { snackbar("No se pudo enviar") }
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 24.dp, vertical = 10.dp)
                    ) {
                        com.musarum.mobile.music.UserAvatar(
                            nick = u.nick,
                            avatarUrl = u.avatarUrl,
                            size = 36.dp
                        )
                        Text(
                            u.nick,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(start = 14.dp)
                        )
                    }
                }
            }
        }
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
    val me by container.userRepository.me.collectAsStateWithLifecycle()
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
                        // Sin snackbar en éxito: la fila desaparece de la
                        // lista al instante y eso basta como feedback.
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
