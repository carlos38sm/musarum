package com.musarum.mobile.music.library

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import kotlinx.coroutines.launch

@Composable
fun AddToPlaylistDialog(
    songId: Long,
    onDismiss: () -> Unit,
    onAdded: (playlistName: String) -> Unit = {}
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    val vm: PlaylistsViewModel = viewModel(
        factory = PlaylistsViewModel.factory(container.playlistRepository)
    )
    val state by vm.state.collectAsState()

    LaunchedEffect(Unit) { vm.refresh() }

    var showCreate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        // Título en fila con cerrar X a la derecha (ahorra espacio al no
        // repetir un botón "Cerrar" abajo) y un botón redondo con "+" a la
        // izquierda que sustituye a la fila "Nueva playlist".
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { showCreate = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = "Nueva playlist",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Text("Añadir a playlist")
                }
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Cerrar",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            when (val s = state) {
                is PlaylistsUiState.Loading -> Box(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is PlaylistsUiState.Error -> Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error
                )

                is PlaylistsUiState.Ready -> Column(modifier = Modifier.fillMaxWidth()) {
                    if (s.playlists.isEmpty()) {
                        Text(
                            "No tienes playlists aún. Pulsa el + de arriba para crear una.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 12.dp)
                        )
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 360.dp)) {
                            items(s.playlists, key = { it.id }) { pl ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            scope.launch {
                                                val result = runCatching {
                                                    container.playlistRepository.addSong(pl.id, songId)
                                                }
                                                if (result.isSuccess) {
                                                    snackbar("Añadida a ${pl.name}")
                                                } else {
                                                    snackbar("No se pudo añadir")
                                                }
                                                onAdded(pl.name)
                                                onDismiss()
                                            }
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Filled.QueueMusic, contentDescription = null)
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        pl.name,
                                        style = MaterialTheme.typography.bodyLarge,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        },
        // El cierre vive en la X del título; no renderizamos nada aquí para
        // no reservar la fila de botones del AlertDialog.
        confirmButton = {}
    )

    if (showCreate) {
        CreatePlaylistDialog(
            onDismiss = { showCreate = false },
            onConfirm = { name, desc, isCollaborative ->
                scope.launch {
                    val result = runCatching {
                        val created = container.playlistRepository.create(
                            name = name,
                            description = desc,
                            isCollaborative = isCollaborative
                        )
                        container.playlistRepository.addSong(created.id, songId)
                        created.name
                    }
                    result.fold(
                        onSuccess = { createdName ->
                            snackbar("Añadida a $createdName")
                            onAdded(createdName)
                        },
                        onFailure = { snackbar("No se pudo crear la playlist") }
                    )
                    showCreate = false
                    onDismiss()
                }
            }
        )
    }
}
