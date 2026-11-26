package com.musarum.mobile.profile.unidentified

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
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Help
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.data.network.dto.SongDetailsDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.player.EditTagsDialog
import kotlinx.coroutines.launch

/**
 * Pantalla OWNER: canciones que Emendatium no supo identificar. Suelen
 * ser las que acabaron en {@code _inbox/sin-identificar/} tras subida.
 * El OWNER puede editarles los tags a mano (reutilizando el EditTags
 * del InfoScreen) → al guardar, el backend reintenta enriquecimiento →
 * si ahora identifica, la canción se mueve sola a la raíz de la
 * biblioteca y desaparece de esta lista.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnidentifiedSongsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: UnidentifiedSongsViewModel = viewModel(
        factory = UnidentifiedSongsViewModel.factory(
            apiService = container.apiService,
            songRepository = container.songRepository
        )
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var editing by remember { mutableStateOf<SongDetailsDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Canciones sin identificar") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Recargar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val s = state) {
                is UnidentifiedUiState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is UnidentifiedUiState.Error -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        s.message,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(24.dp)
                    )
                }

                is UnidentifiedUiState.Ready -> {
                    if (s.songs.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                Icons.Filled.Help,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.size(12.dp))
                            Text(
                                "Nada sin identificar. Emendatium las supo mover todas.",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            Text(
                                "Emendatium no supo identificar estas ${s.songs.size}. " +
                                    "Edita los tags (artista / título) y reintentará solo.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                            )
                            HorizontalDivider()
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(s.songs, key = { it.id }) { song ->
                                    UnidentifiedRow(
                                        song = song,
                                        onEdit = {
                                            viewModel.loadDetails(
                                                id = song.id,
                                                onReady = { editing = it },
                                                onError = { msg ->
                                                    scope.launch { snackbar.showSnackbar(msg) }
                                                }
                                            )
                                        },
                                        onRetry = {
                                            viewModel.reEnrich(song.id) { _, msg ->
                                                scope.launch { snackbar.showSnackbar(msg) }
                                            }
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
    }

    // EditTagsDialog en el nivel raíz para que su overlay quede encima
    // del Scaffold y del SnackbarHost. Al guardar, lanzamos el save en
    // el VM con reEnrichAfter=true para que Emendatium reintente con
    // los tags nuevos; si identifica, la canción se mueve y desaparece
    // de esta lista en el próximo refresh (automático a los 1.5s).
    editing?.let { details ->
        EditTagsDialog(
            details = details,
            onDismiss = { editing = null },
            onSave = { request ->
                editing = null
                viewModel.saveTags(
                    id = details.id,
                    request = request,
                    reEnrichAfter = true
                ) { _, msg ->
                    scope.launch { snackbar.showSnackbar(msg) }
                }
            }
        )
    }
}

@Composable
private fun UnidentifiedRow(
    song: SongSummaryDto,
    onEdit: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artistsLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Editar tags")
        }
        Spacer(Modifier.width(4.dp))
        IconButton(onClick = onRetry) {
            Icon(Icons.Filled.Replay, contentDescription = "Reintentar")
        }
    }
}
