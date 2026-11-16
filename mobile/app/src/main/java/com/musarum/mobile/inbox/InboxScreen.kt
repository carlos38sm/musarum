package com.musarum.mobile.inbox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.data.network.dto.InboxItemDto
import com.musarum.mobile.music.ArtistAvatar
import com.musarum.mobile.music.CoverImage
import kotlinx.coroutines.launch

/**
 * Bandeja de canciones compartidas por otros users de la red. Cada item
 * tiene remitente, canción, nota opcional y un botón ▶ para reproducir.
 * Un swipe/tap al cubo elimina. El badge del topbar del Home se actualiza
 * al marcar como leída (que ocurre al reproducir o al dejar la bandeja).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InboxScreen(
    onBack: () -> Unit,
    onOpenArtist: (Long) -> Unit = {}
) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<InboxItemDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching { container.apiService.getInbox() }
            .onSuccess { items = it }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Bandeja") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { inner ->
        if (items.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) {
                Text(
                    if (loading) "Cargando…" else "Nada compartido todavía",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            return@Scaffold
        }
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(inner),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(items, key = { it.id }) { item ->
                InboxRow(
                    item = item,
                    onPlay = {
                        val song = item.song ?: return@InboxRow
                        container.playerController.playQueue(listOf(song), 0)
                        if (!item.read) scope.launch {
                            runCatching { container.apiService.markInboxRead(item.id) }
                            items = items.map {
                                if (it.id == item.id) it.copy(read = true) else it
                            }
                        }
                    },
                    onDelete = {
                        scope.launch {
                            runCatching { container.apiService.deleteInbox(item.id) }
                                .onSuccess { items = items.filter { it.id != item.id } }
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun InboxRow(
    item: InboxItemDto,
    onPlay: () -> Unit,
    onDelete: () -> Unit
) {
    val bg = if (!item.read) MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
             else androidx.compose.ui.graphics.Color.Transparent
    Surface(color = bg, modifier = Modifier.fillMaxWidth().clickable(onClick = onPlay)) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ArtistAvatar(url = item.fromAvatarUrl, size = 40.dp)
            Column(
                modifier = Modifier.weight(1f).padding(start = 12.dp)
            ) {
                Text(
                    text = "${item.fromNick ?: "?"} te envió:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CoverImage(
                        url = item.song?.coverUrl,
                        size = 32.dp,
                        cornerRadius = 4.dp
                    )
                    Column(
                        modifier = Modifier.padding(start = 8.dp).weight(1f)
                    ) {
                        Text(
                            text = item.song?.title ?: "(canción borrada)",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1
                        )
                        if (!item.song?.artistsLabel.isNullOrBlank()) {
                            Text(
                                text = item.song?.artistsLabel.orEmpty(),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
                if (!item.note.isNullOrBlank()) {
                    Text(
                        text = "“${item.note}”",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            IconButton(onClick = onPlay) {
                Icon(
                    Icons.Filled.PlayArrow,
                    contentDescription = "Reproducir",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.Delete,
                    contentDescription = "Eliminar",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
