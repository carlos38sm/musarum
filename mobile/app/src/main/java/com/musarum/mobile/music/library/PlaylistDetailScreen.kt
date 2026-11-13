package com.musarum.mobile.music.library

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.musarum.mobile.data.network.dto.CollaboratorRefDto
import com.musarum.mobile.data.network.dto.PlaylistEntryDto
import com.musarum.mobile.data.network.dto.UserResponseDto
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.ui.InlineSearchBar
import com.musarum.mobile.ui.SearchActionIcon
import com.musarum.mobile.ui.humanizeDurationMs
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit
) {
    val container = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val viewModel: PlaylistDetailViewModel = viewModel(
        key = "playlist_$playlistId",
        factory = PlaylistDetailViewModel.factory(container.playlistRepository, playlistId)
    )
    val state by viewModel.state.collectAsState()
    var showInvite by remember { mutableStateOf(false) }
    // Lupa integrada: solo aparece el icono en la TopAppBar; al tocarlo se
    // despliega la InlineSearchBar y la lista filtra por title/artistsLabel.
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text("Playlist") },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                        }
                    },
                    actions = {
                        SearchActionIcon(active = searching, onToggle = {
                            searching = !searching
                            if (!searching) query = ""
                        })
                    }
                )
                InlineSearchBar(
                    visible = searching,
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Buscar en la playlist"
                )
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is PlaylistDetailUiState.Loading -> Box(
                    Modifier.fillMaxSize(), Alignment.Center
                ) { CircularProgressIndicator() }

                is PlaylistDetailUiState.Error -> Box(
                    Modifier.fillMaxSize(), Alignment.Center
                ) { Text(s.message, color = MaterialTheme.colorScheme.error) }

                is PlaylistDetailUiState.Ready -> {
                    val allEntries = s.playlist.entries.content
                    // Filtro case-insensitive sobre título + artistas. La lupa
                    // siempre busca sobre el conjunto completo, aunque el
                    // backend haya paginado — para las 7k canciones una
                    // playlist rara vez pasa de unos cientos, es viable en
                    // memoria. Si el usuario cerró la lupa (searching=false),
                    // la query ya se limpió → filter no cambia nada.
                    val entries = if (query.isBlank()) allEntries else {
                        val q = query.trim().lowercase()
                        remember(query, allEntries) {
                            allEntries.filter {
                                it.songTitle.lowercase().contains(q) ||
                                    it.artists.any { a -> a.name.lowercase().contains(q) }
                            }
                        }
                    }
                    val lazyListState = androidx.compose.foundation.lazy.rememberLazyListState()
                    // Reorderable: capturamos los movimientos durante el drag
                    // y los aplicamos optimistamente al VM. El VM re-emite el
                    // estado y la lista refleja la nueva posición. Al soltar
                    // (onDragStopped) hacemos el POST al backend.
                    val reorderableState = rememberReorderableLazyListState(
                        lazyListState = lazyListState
                    ) { from, to ->
                        // Si hay query activa la lista mostrada es un subset.
                        // No reordenamos en ese estado porque los índices
                        // filtrados no coinciden con los reales en DB — el
                        // handle también se oculta, así que este bloqueo es
                        // una red de seguridad extra.
                        if (query.isNotBlank()) return@rememberReorderableLazyListState
                        val fromKey = from.key as? Long ?: return@rememberReorderableLazyListState
                        val toKey = to.key as? Long ?: return@rememberReorderableLazyListState
                        val fromIdx = allEntries.indexOfFirst { it.entryId == fromKey }
                        val toIdx = allEntries.indexOfFirst { it.entryId == toKey }
                        if (fromIdx >= 0 && toIdx >= 0) {
                            viewModel.moveEntry(fromKey, fromIdx, toIdx)
                        }
                    }
                    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                Text(
                                    s.playlist.name,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                s.playlist.description?.takeIf { it.isNotBlank() }?.let {
                                    Text(
                                        it,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                                val totalMs = entries.sumOf { (it.durationMs ?: 0).toLong() }
                                Text(
                                    "${entries.size} canciones · ${humanizeDurationMs(totalMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                                Spacer(Modifier.height(12.dp))
                                val downloaded by container.downloadsRepository
                                    .entries.collectAsState()
                                val pending = entries.count { downloaded[it.songId] == null }
                                val allDownloaded = entries.isNotEmpty() && pending == 0
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            if (entries.isNotEmpty()) {
                                                container.playerController.playQueue(
                                                    songs = entries.map { it.toSongSummary() },
                                                    startIndex = 0
                                                )
                                            }
                                        },
                                        enabled = entries.isNotEmpty()
                                    ) {
                                        Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                        Spacer(Modifier.width(6.dp))
                                        Text("Reproducir")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            // Encolamos solo las que no están ya descargadas o
                                            // en curso: el manager ya es idempotente, pero así
                                            // le ahorramos ir y volver por el mutex N veces.
                                            val toDownload = entries
                                                .map { it.toSongSummary() }
                                                .filter { downloaded[it.id] == null }
                                            toDownload.forEach {
                                                container.downloadsManager.download(it)
                                            }
                                            snackbar(when {
                                                toDownload.isEmpty() -> "Ya estaban todas descargadas"
                                                else -> "Descargando ${toDownload.size} canciones"
                                            })
                                        },
                                        enabled = entries.isNotEmpty() && !allDownloaded
                                    ) {
                                        Icon(
                                            if (allDownloaded) Icons.Filled.CloudDone
                                            else Icons.Filled.CloudDownload,
                                            contentDescription = null
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(when {
                                            allDownloaded -> "Descargada"
                                            pending == entries.size -> "Descargar"
                                            else -> "Descargar ($pending)"
                                        })
                                    }
                                }

                                Spacer(Modifier.height(12.dp))
                                CollaboratorsSection(
                                    collaborators = s.playlist.collaborators,
                                    onInvite = { showInvite = true },
                                    onRemove = { userId ->
                                        viewModel.removeCollaborator(userId) { _, msg ->
                                            snackbar(msg)
                                        }
                                    }
                                )
                            }
                            HorizontalDivider()
                        }

                        if (entries.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "Esta playlist está vacía",
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        } else {
                            items(entries, key = { it.entryId }) { entry ->
                                ReorderableItem(
                                    state = reorderableState,
                                    key = entry.entryId
                                ) { isDragging ->
                                    EntryRow(
                                        entry = entry,
                                        onClick = {
                                            container.playerController.playQueue(
                                                songs = entries.map { it.toSongSummary() },
                                                startIndex = entries.indexOf(entry)
                                            )
                                        },
                                        onRemove = { viewModel.removeEntry(entry.entryId) },
                                        isDragging = isDragging,
                                        dragHandleModifier = Modifier.draggableHandle(),
                                        dragEnabled = query.isBlank()
                                    )
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    if (showInvite) {
        InviteCollaboratorDialog(
            onDismiss = { showInvite = false },
            onSearch = { q -> viewModel.searchUsers(q) },
            onPick = { user ->
                viewModel.addCollaborator(user.nick) { ok, msg ->
                    snackbar(msg)
                    if (ok) showInvite = false
                }
            }
        )
    }
}

@Composable
private fun CollaboratorsSection(
    collaborators: List<CollaboratorRefDto>,
    onInvite: () -> Unit,
    onRemove: (Long) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Colaboradores",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onInvite) {
                Icon(Icons.Filled.PersonAdd, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("Invitar")
            }
        }
        if (collaborators.isEmpty()) {
            Text(
                "Nadie más puede editarla todavía.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(top = 6.dp)
            ) {
                items(collaborators, key = { it.id }) { c ->
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                    ) {
                        Row(
                            modifier = Modifier.padding(start = 12.dp, end = 6.dp, top = 4.dp, bottom = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                c.nick,
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = { onRemove(c.id) },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Close,
                                    contentDescription = "Quitar colaborador",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InviteCollaboratorDialog(
    onDismiss: () -> Unit,
    onSearch: suspend (String) -> List<UserResponseDto>,
    onPick: (UserResponseDto) -> Unit
) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<UserResponseDto>>(emptyList()) }

    // Debounce: tras 300ms de inactividad, lanza búsqueda. Si el campo queda
    // vacío, limpia resultados sin pedir nada.
    LaunchedEffect(query) {
        if (query.isBlank()) {
            results = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        results = runCatching { onSearch(query) }.getOrDefault(emptyList())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Invitar colaborador") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    label = { Text("Buscar por nick") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                if (results.isEmpty() && query.isNotBlank()) {
                    Text(
                        "Sin resultados",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    results.forEach { user ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(user) }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                modifier = Modifier.size(34.dp),
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        user.nick.firstOrNull()?.uppercase() ?: "?",
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Spacer(Modifier.width(12.dp))
                            Text(user.nick, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}

/**
 * Fila de la playlist. Soporta drag-and-drop: el icono {@code DragHandle}
 * a la derecha toma el gesto (modifier `.draggableHandle()` que inyecta el
 * caller desde ReorderableItem). Cuando se está arrastrando, elevamos la
 * fila con shadow + fondo para que destaque.
 */
@Composable
private fun EntryRow(
    entry: PlaylistEntryDto,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    dragEnabled: Boolean = true
) {
    val elevation = if (isDragging) 8.dp else 0.dp
    androidx.compose.material3.Surface(
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = elevation,
        color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                else androidx.compose.ui.graphics.Color.Transparent
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            CoverImage(url = entry.coverUrl, size = 48.dp, cornerRadius = 6.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.songTitle,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val subtitle = entry.artists.joinToString(", ") { it.name }
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
            entry.musicalKey?.takeIf { it.isNotBlank() }?.let { key ->
                com.musarum.mobile.music.CamelotBadge(musicalKey = key)
                Spacer(Modifier.width(6.dp))
            }
            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.RemoveCircleOutline,
                    contentDescription = "Quitar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Handle de arrastre — modifier.draggableHandle() lo inyecta
            // ReorderableItem desde el call site, aquí solo lo aplicamos.
            // Oculto cuando dragEnabled=false (p. ej. con lupa activa):
            // reordenar sobre un subset filtrado produciría posiciones
            // inconsistentes con DB.
            if (dragEnabled) {
                Icon(
                    imageVector = Icons.Filled.DragIndicator,
                    contentDescription = "Arrastrar para reordenar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = dragHandleModifier.padding(start = 4.dp)
                )
            }
        }
    }
}

