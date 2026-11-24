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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.music.CoverImage
import kotlinx.coroutines.launch

/**
 * Pantalla "Añadir canciones" (selección múltiple). Trae todo el
 * catálogo con una sola petición (/songs/all) y permite marcar varias
 * con tap. El FAB de abajo manda la lista completa al backend con el
 * endpoint bulk — una única request, mucho más barato que N POST.
 *
 * Filtra en memoria por título / artista con el campo de búsqueda.
 * Oculta las canciones que ya están en la playlist actual: mismo
 * patrón que haría Spotify, evita duplicados y simplifica la lista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickSongsForPlaylistScreen(
    playlistId: Long,
    onBack: () -> Unit,
    onDone: () -> Unit
) {
    val container = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    // Catálogo completo. Lo cargamos una sola vez al entrar; con gzip +
    // ETag el impacto en red es asumible incluso con 7k canciones. Si
    // crece a 20k+, migrar a paginación lazy.
    var allSongs by remember { mutableStateOf<List<SongSummaryDto>?>(null) }
    // Canciones que YA están dentro de esta playlist — las ocultamos
    // de la lista para no duplicar y para que la "capacidad útil" sea
    // obvia visualmente.
    var alreadyInIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var loadError by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(playlistId) {
        try {
            val cat = container.songRepository.allOrdered()
            val details = container.playlistRepository.details(playlistId)
            alreadyInIds = details.entries.content.map { it.songId }.toSet()
            allSongs = cat
        } catch (t: Throwable) {
            loadError = t.message ?: "No se pudieron cargar las canciones"
        }
    }

    val selected = rememberSaveable { mutableStateOf<Set<Long>>(emptySet()) }
    var query by rememberSaveable { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selected.value.isEmpty()) "Añadir canciones"
                        else "${selected.value.size} seleccionadas"
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selected.value.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = {
                        val ids = selected.value.toList()
                        scope.launch {
                            runCatching {
                                container.playlistRepository.addSongsBulk(playlistId, ids)
                            }.onSuccess {
                                snackbar("Añadidas ${ids.size} canciones")
                                onDone()
                            }.onFailure {
                                snackbar(it.message ?: "No se pudieron añadir")
                            }
                        }
                    },
                    icon = { Icon(Icons.Filled.Check, contentDescription = null) },
                    text = { Text("Añadir ${selected.value.size}") }
                )
            }
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Buscar por título o artista") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            when {
                loadError != null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        loadError ?: "",
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                allSongs == null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }
                else -> {
                    val q = query.trim().lowercase()
                    val filtered = allSongs!!.asSequence()
                        .filter { !alreadyInIds.contains(it.id) }
                        .filter {
                            q.isBlank() ||
                                it.title.lowercase().contains(q) ||
                                it.artistsLabel.lowercase().contains(q)
                        }
                        .toList()
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(filtered, key = { it.id }) { song ->
                            val isSelected = selected.value.contains(song.id)
                            PickerSongRow(
                                song = song,
                                selected = isSelected,
                                onToggle = {
                                    selected.value = if (isSelected)
                                        selected.value - song.id
                                    else selected.value + song.id
                                }
                            )
                            HorizontalDivider()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerSongRow(
    song: SongSummaryDto,
    selected: Boolean,
    onToggle: () -> Unit
) {
    // Selección por resaltado (fondo primary semi-transparente + check
    // pequeño en la portada), sin checkboxes a la izquierda. Pedido
    // Carlos nov-24-pm: los cuadraditos quedaban feos. El toggle sigue
    // siendo tap en toda la fila.
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
             else androidx.compose.ui.graphics.Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(bg)
            .clickable(onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(48.dp)) {
            CoverImage(
                url = song.coverUrl,
                size = 48.dp,
                cornerRadius = 6.dp,
                placeholderKind = com.musarum.mobile.music.PlaceholderKind.SONGS,
                placeholderSeed = song.id
            )
            if (selected) {
                // Overlay translúcido + check en el centro: feedback claro
                // de "esta canción está seleccionada".
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                            androidx.compose.foundation.shape.RoundedCornerShape(6.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        androidx.compose.material.icons.Icons.Filled.Check,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (song.artistsLabel.isNotBlank()) {
                Text(
                    song.artistsLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}
