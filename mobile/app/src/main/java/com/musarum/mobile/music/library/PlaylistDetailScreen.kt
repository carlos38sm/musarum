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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.foundation.background
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
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
import com.musarum.mobile.ui.humanizeIso
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onAddSongs: () -> Unit = {}
) {
    val container = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val viewModel: PlaylistDetailViewModel = viewModel(
        key = "playlist_$playlistId",
        factory = PlaylistDetailViewModel.factory(container.playlistRepository, playlistId)
    )
    val state by viewModel.state.collectAsState()

    // Mientras la pantalla esté viva, polling suave cada 10s para ver en
    // vivo lo que otros colaboradores añadan/quiten/muevan. El backend
    // responde 304 cuando no cambió nada (ETag), así que el coste real
    // en los ticks tranquilos es mínimo.
    androidx.compose.runtime.DisposableEffect(playlistId) {
        viewModel.startLivePolling()
        onDispose { viewModel.stopLivePolling() }
    }

    var showInvite by remember { mutableStateOf(false) }
    // Lupa integrada: solo aparece el icono en la TopAppBar; al tocarlo se
    // despliega la InlineSearchBar y la lista filtra por title/artistsLabel.
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    // Modo "ordenar": al activarlo aparecen las asas de drag en cada fila
    // (hasta entonces están ocultas, estilo Spotify). Antes el drag estaba
    // siempre visible y manchaba la lista.
    var reorderMode by remember { mutableStateOf(false) }
    var showEditInfo by remember { mutableStateOf(false) }

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
                    // State que necesita el LazyColumn (favoritos del user y
                    // scope de corrutina para el toggle via swipe). Van FUERA
                    // porque collectAsState / rememberCoroutineScope son
                    // @Composable y el bloque del LazyColumn body no lo es.
                    val favoriteIds = container.favoriteRepository.favorites
                        .collectAsState().value.map { it.songId }.toSet()
                    val favoriteScope = androidx.compose.runtime.rememberCoroutineScope()

                    LazyColumn(state = lazyListState, modifier = Modifier.fillMaxSize()) {
                        item {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp)
                            ) {
                                // Mosaico 2×2 + título como header (B20).
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    com.musarum.mobile.music.PlaylistMosaicCover(
                                        urls = s.playlist.coverUrls,
                                        playlistId = s.playlist.id,
                                        size = 112.dp,
                                        cornerRadius = 12.dp
                                    )
                                    Spacer(Modifier.width(14.dp))
                                    Column(modifier = Modifier.weight(1f)) {
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
                                                maxLines = 2,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.padding(top = 4.dp)
                                            )
                                        }
                                    }
                                }
                                Spacer(Modifier.height(14.dp))
                                // Info extra (B21) en bento 2×2: canciones,
                                // duración total, artista top, género top.
                                // La fecha va aparte como pie pequeño.
                                val totalMs = s.playlist.durationMs
                                    ?: entries.sumOf { (it.durationMs ?: 0).toLong() }
                                val songCount = s.playlist.songCount.coerceAtLeast(entries.size)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    PlaylistStatTile(
                                        value = songCount.toString(),
                                        label = "canciones",
                                        modifier = Modifier.weight(1f)
                                    )
                                    PlaylistStatTile(
                                        value = humanizeDurationMs(totalMs),
                                        label = "duración",
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (s.playlist.topArtistName != null || s.playlist.topGenreName != null) {
                                    Spacer(Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        PlaylistStatTile(
                                            value = s.playlist.topArtistName ?: "—",
                                            label = "artista top",
                                            modifier = Modifier.weight(1f)
                                        )
                                        PlaylistStatTile(
                                            value = s.playlist.topGenreName ?: "—",
                                            label = "género top",
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                                s.playlist.createdAt?.let { iso ->
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        "Creada ${humanizeIso(iso)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                                    )
                                }
                                Spacer(Modifier.height(12.dp))
                                val downloaded by container.downloadsRepository
                                    .entries.collectAsState()
                                val pending = entries.count { downloaded[it.songId] == null }
                                val allDownloaded = entries.isNotEmpty() && pending == 0
                                val me by container.userRepository.me.collectAsState()
                                val isOwner = me?.id != null && me?.id == s.playlist.ownerId
                                // Fila de acciones icon-only pedida por Carlos:
                                // reproducir, descargar, editar, ordenar, invitar.
                                // Sin texto, más compacta. Los últimos 3 solo
                                // aparecen si eres owner (el backend rechazaría
                                // las acciones de otros con 404).
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
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
                                        Icon(
                                            Icons.Filled.PlayArrow,
                                            contentDescription = "Reproducir",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            val toDownload = entries
                                                .map { it.toSongSummary() }
                                                .filter { downloaded[it.id] == null }
                                            toDownload.forEach {
                                                container.downloadsManager.download(it)
                                            }
                                        },
                                        enabled = entries.isNotEmpty() && !allDownloaded
                                    ) {
                                        Icon(
                                            if (allDownloaded) Icons.Filled.CloudDone
                                            else Icons.Filled.CloudDownload,
                                            contentDescription = if (allDownloaded) "Descargada"
                                            else "Descargar ${if (pending < entries.size) "($pending)" else ""}",
                                            tint = if (allDownloaded) MaterialTheme.colorScheme.tertiary
                                            else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    // El "añadir canciones en lote" lo pueden
                                    // usar tanto owner como colaboradores
                                    // (canEdit en el backend acepta a los dos).
                                    val canEdit = isOwner ||
                                        (me?.id != null &&
                                            s.playlist.collaborators.any { it.id == me?.id })
                                    if (canEdit) {
                                        IconButton(onClick = onAddSongs) {
                                            Icon(
                                                Icons.Filled.Add,
                                                contentDescription = "Añadir canciones"
                                            )
                                        }
                                    }
                                    if (isOwner) {
                                        IconButton(onClick = { showEditInfo = true }) {
                                            Icon(
                                                Icons.Filled.Edit,
                                                contentDescription = "Editar info"
                                            )
                                        }
                                        IconButton(
                                            onClick = { reorderMode = !reorderMode },
                                            enabled = entries.isNotEmpty() && query.isBlank()
                                        ) {
                                            Icon(
                                                Icons.Filled.SwapVert,
                                                contentDescription = if (reorderMode) "Terminar orden" else "Ordenar",
                                                tint = if (reorderMode) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        IconButton(onClick = { showInvite = true }) {
                                            Icon(
                                                Icons.Filled.PersonAdd,
                                                contentDescription = "Invitar"
                                            )
                                        }
                                    }
                                }

                                // Bolitas de colaboradores (estilo Spotify): solo
                                // si la playlist tiene alguno. Cuando no hay
                                // nadie, ni se dibuja la sección — antes
                                // aparecía una tarjeta pálida con "nadie puede
                                // editarla todavía" que ensuciaba el header.
                                if (s.playlist.collaborators.isNotEmpty()) {
                                    Spacer(Modifier.height(10.dp))
                                    CollaboratorsBubbles(
                                        collaborators = s.playlist.collaborators,
                                        canManage = isOwner,
                                        onRemove = { userId ->
                                            viewModel.removeCollaborator(userId) { _, msg ->
                                                snackbar(msg)
                                            }
                                        }
                                    )
                                }
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
                                        isFavorite = favoriteIds.contains(entry.songId),
                                        onClick = {
                                            container.playerController.playQueue(
                                                songs = entries.map { it.toSongSummary() },
                                                startIndex = entries.indexOf(entry)
                                            )
                                        },
                                        onRemove = { viewModel.removeEntry(entry.entryId) },
                                        onToggleFavorite = {
                                            favoriteScope.launch {
                                                container.favoriteRepository.toggle(entry.songId)
                                            }
                                        },
                                        isDragging = isDragging,
                                        dragHandleModifier = Modifier.draggableHandle(),
                                        dragEnabled = reorderMode && query.isBlank()
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

    if (showEditInfo) {
        val current = (state as? PlaylistDetailUiState.Ready)?.playlist
        if (current != null) {
            EditPlaylistInfoDialog(
                initialName = current.name,
                initialDescription = current.description.orEmpty(),
                initialPublic = current.isPublic,
                onDismiss = { showEditInfo = false },
                onSave = { newName, newDesc, newPublic ->
                    viewModel.updateInfo(newName, newDesc.takeIf { it.isNotBlank() }, newPublic) { ok, msg ->
                        snackbar(msg)
                        if (ok) showEditInfo = false
                    }
                }
            )
        }
    }
}

/**
 * Mini-tile tipo bento para los stats del header de playlist. Valor en
 * grande arriba (titleMedium bold), label pequeña debajo. Fondo con
 * surfaceVariant + esquinas redondeadas 14dp — queda como "pastilla"
 * discreta, no como botón.
 */
@Composable
private fun PlaylistStatTile(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EditPlaylistInfoDialog(
    initialName: String,
    initialDescription: String,
    initialPublic: Boolean,
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, isPublic: Boolean) -> Unit
) {
    var name by remember { mutableStateOf(initialName) }
    var description by remember { mutableStateOf(initialDescription) }
    // isPublic deja de exponerse al usuario — el concepto "colaborativa" se
    // maneja desde los detalles invitando por nick. Conservamos el flag en
    // el callback (y el valor inicial) por compat con el backend y por si
    // volvemos a añadir un modo "visible por cualquier user".

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Editar playlist") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Descripción") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), description.trim(), initialPublic) },
                enabled = name.isNotBlank()
            ) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

/**
 * Fila de bolitas de colaboradores estilo Spotify. Solo se usa cuando ya
 * hay colaboradores; si el owner quiere añadir uno, usa el icono
 * PersonAdd de la fila de acciones (abre InviteCollaboratorDialog).
 */
@Composable
private fun CollaboratorsBubbles(
    collaborators: List<CollaboratorRefDto>,
    canManage: Boolean,
    onRemove: (Long) -> Unit
) {
    if (collaborators.isEmpty()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        collaborators.forEach { c ->
            Box {
                com.musarum.mobile.music.UserAvatar(
                    nick = c.nick,
                    // Si el user subió foto, se pinta; si no, cae a la
                    // inicial coloreada (la lógica vive en UserAvatar).
                    avatarUrl = c.avatarUrl,
                    size = 32.dp,
                    modifier = Modifier.padding(end = 6.dp)
                )
                if (canManage) {
                    // X en la esquina superior derecha para quitar. Hitbox
                    // pequeño pero accesible. No se muestra a los MEMBER.
                    IconButton(
                        onClick = { onRemove(c.id) },
                        modifier = Modifier
                            .size(16.dp)
                            .align(Alignment.TopEnd)
                    ) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Quitar ${c.nick}",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Antigua tarjeta "Colaboradores" — queda sin uso pero la dejo por si se
 * reutiliza en el futuro para una vista más detallada.
 */
@Suppress("unused")
@Composable
private fun CollaboratorsSection(
    collaborators: List<CollaboratorRefDto>,
    canManage: Boolean,
    onInvite: () -> Unit,
    onRemove: (Long) -> Unit
) {
    // Si no soy owner y no hay colaboradores → nada que mostrar en absoluto.
    if (!canManage && collaborators.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Colaboradores",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            if (canManage) {
                TextButton(onClick = onInvite) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Invitar")
                }
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
                            if (canManage) {
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
                            } else {
                                Spacer(Modifier.width(12.dp))
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
    // Nuevo diseño pedido por Carlos (nov 2026): bolitas de los usuarios
    // directamente visibles para pulsar una sin escribir nada. La lupa
    // revela un campo de búsqueda solo cuando hace falta (hay muchos
    // usuarios o el que buscas no se ve). Con 3 o 4 oyentes en la red,
    // normalmente con las bolitas sobra.
    var allUsers by remember { mutableStateOf<List<UserResponseDto>>(emptyList()) }
    var showSearch by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    var searchResults by remember { mutableStateOf<List<UserResponseDto>>(emptyList()) }
    val container = LocalAppContainer.current
    val me by container.userRepository.me.collectAsState()

    // Carga inicial: /auth/users devuelve TODOS los usuarios registrados
    // (público, sin auth) — perfecto para la rejilla de bolitas. Lo
    // convertimos al DTO que usa el resto del flujo (nick + avatar).
    LaunchedEffect(Unit) {
        allUsers = runCatching { container.apiService.getLoginUsers() }
            .getOrDefault(emptyList())
            .filter { it.id != me?.id }
            .map { lu ->
                UserResponseDto(
                    id = lu.id,
                    nick = lu.nick,
                    avatarUrl = lu.avatarUrl
                )
            }
    }
    LaunchedEffect(query, showSearch) {
        if (!showSearch || query.isBlank()) {
            searchResults = emptyList()
            return@LaunchedEffect
        }
        delay(300)
        searchResults = runCatching { onSearch(query) }.getOrDefault(emptyList())
    }

    val visible = if (showSearch && query.isNotBlank()) searchResults else allUsers

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Invitar", modifier = Modifier.weight(1f))
                IconButton(onClick = { showSearch = !showSearch; if (!showSearch) query = "" }) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = if (showSearch) "Ocultar búsqueda" else "Buscar por nick",
                        tint = if (showSearch) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (showSearch) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Buscar por nick") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(10.dp))
                }
                if (visible.isEmpty()) {
                    Text(
                        if (showSearch && query.isNotBlank()) "Sin resultados"
                        else "No hay más usuarios disponibles.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // FlowRow de bolitas: cada una con avatar + nick debajo.
                    // Tap = invitar. maxItemsInEachRow = 4 funciona bien con
                    // 3 compañeros; si crece, hace scroll de columnas.
                    androidx.compose.foundation.layout.FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterHorizontally),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        maxItemsInEachRow = 4
                    ) {
                        visible.forEach { user ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .width(68.dp)
                                    .clickable { onPick(user) }
                            ) {
                                com.musarum.mobile.music.UserAvatar(
                                    nick = user.nick,
                                    avatarUrl = user.avatarUrl,
                                    size = 56.dp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    user.nick,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                )
                            }
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
 * Fila de la playlist. Dos gestos:
 *   - Swipe izquierda (EndToStart) → eliminar la canción de la playlist.
 *     Fondo rojo con icono papelera; al soltar, la fila sale.
 *   - Swipe derecha (StartToEnd) → alternar favorito. Fondo primary
 *     con corazón; al soltar, la fila vuelve a su sitio y el icono
 *     actualiza su estado.
 *   - Drag-and-drop con el icono DragHandle para reordenar (solo con
 *     reorderMode activo y sin lupa; el modifier draggableHandle lo
 *     inyecta el ReorderableItem).
 *
 * El dismiss izquierda confirma su "going away" (confirmValueChange=true
 * para EndToStart) y llama onRemove; el derecha NO confirma (false) y
 * usa el callback como pulso: la fila regresa animada al centro y el
 * estado de favorito ya ha cambiado en memoria vía FavoriteRepository.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EntryRow(
    entry: PlaylistEntryDto,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    onToggleFavorite: () -> Unit,
    isDragging: Boolean = false,
    dragHandleModifier: Modifier = Modifier,
    dragEnabled: Boolean = true
) {
    val elevation = if (isDragging) 8.dp else 0.dp
    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { newValue ->
            when (newValue) {
                SwipeToDismissBoxValue.EndToStart -> {
                    // Swipe a la izquierda = quitar de la playlist.
                    // Confirmamos la dismiss: la fila desaparece con
                    // animación, el backend se encargará de restar la
                    // entry y el flow reemitirá la lista sin ella.
                    onRemove()
                    true
                }
                SwipeToDismissBoxValue.StartToEnd -> {
                    // Swipe a la derecha = toggle favorito. No queremos
                    // que la fila desaparezca — es un pulso visual.
                    onToggleFavorite()
                    false
                }
                else -> false
            }
        },
        // Umbral del 35% del ancho para disparar — más bajo que el
        // default (50%) para que el gesto se note confortable con el
        // pulgar sin requerir recorrido completo.
        positionalThreshold = { totalDistance: Float -> totalDistance * 0.35f }
    )

    SwipeToDismissBox(
        state = dismissState,
        backgroundContent = {
            SwipeBackground(
                direction = dismissState.dismissDirection,
                isFavorite = isFavorite
            )
        },
        // El drag reordenable usa el handle lateral; el swipe horizontal
        // no debe interferir mientras estás arrastrando con los dedos
        // para reordenar: desactivamos ambas direcciones si isDragging.
        enableDismissFromStartToEnd = !isDragging,
        enableDismissFromEndToStart = !isDragging,
        modifier = Modifier.fillMaxWidth()
    ) {
        androidx.compose.material3.Surface(
            modifier = Modifier.fillMaxWidth(),
            shadowElevation = elevation,
            color = if (isDragging) MaterialTheme.colorScheme.surfaceVariant
                    else MaterialTheme.colorScheme.background
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
                // Mini-icono "favorito activo" como feedback visual: si
                // la canción ya es favorita, se ve un corazón pequeño
                // aquí. El botón "quitar" clásico se fue — ahora es el
                // swipe izquierda.
                if (isFavorite) {
                    Icon(
                        Icons.Filled.Favorite,
                        contentDescription = null,
                        tint = com.musarum.mobile.ui.FavoriteRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
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
}

/**
 * Fondo que asoma bajo la fila al deslizar. Sentido visual:
 *   - StartToEnd (swipe derecha): barra con primary a la izquierda +
 *     icono ♥ (relleno si ya es favorita, línea si no). "Añadir/quitar".
 *   - EndToStart (swipe izquierda): barra con error a la derecha +
 *     icono papelera. "Eliminar de la playlist".
 */
@Composable
private fun SwipeBackground(
    direction: SwipeToDismissBoxValue,
    isFavorite: Boolean
) {
    when (direction) {
        SwipeToDismissBoxValue.StartToEnd -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primary)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.FavoriteBorder
                              else Icons.Filled.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                if (isFavorite) "Quitar de favoritas" else "Añadir a favoritas",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onPrimary,
                fontWeight = FontWeight.SemiBold
            )
        }
        SwipeToDismissBoxValue.EndToStart -> Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.error)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                "Quitar de la playlist",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onError,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.width(10.dp))
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onError,
                modifier = Modifier.size(28.dp)
            )
        }
        SwipeToDismissBoxValue.Settled -> {
            // En reposo no se debería ver el fondo; dejamos un Box vacío
            // del tamaño de la fila para que el layout no colapse.
            Box(modifier = Modifier.fillMaxWidth())
        }
    }
}

