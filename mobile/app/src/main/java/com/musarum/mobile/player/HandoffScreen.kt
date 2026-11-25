package com.musarum.mobile.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DevicesOther
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.data.network.dto.HandoffDto
import com.musarum.mobile.data.network.dto.PublishHandoffRequest
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.music.UserAvatar
import kotlinx.coroutines.launch

/**
 * Transferir reproducción entre dispositivos. Cada móvil puede:
 *   - "Publicar mi estado" (POST /handoff/publish): envía al backend
 *     qué canción está sonando ahora + en qué segundo + un label del
 *     dispositivo (p.ej. "Pixel 7 de Carlos").
 *   - Ver una lista de handoffs activos (GET /handoff) con los estados
 *     publicados en las últimas 48 h.
 *   - Tocar una entrada â†’ arranca esa canción localmente desde esa
 *     posición. El handoff queda consumido en el servidor para que no
 *     parezca que sigue sonando en el otro dispositivo.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HandoffScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val playerState by container.playerController.state.collectAsStateWithLifecycle()
    var items by remember { mutableStateOf<List<HandoffDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        runCatching { container.apiService.listHandoffs() }
            .onSuccess { items = it }
        loading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Continuar en otro dispositivo") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { inner ->
        Column(modifier = Modifier.fillMaxSize().padding(inner)) {
            // Publicar mi estado actual.
            val currentSong = playerState.currentSong
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = {
                        if (currentSong == null) {
                            snackbar("No hay canción sonando ahora")
                            return@Button
                        }
                        scope.launch {
                            val deviceLabel = android.os.Build.MODEL ?: "Dispositivo"
                            val res = runCatching {
                                container.apiService.publishHandoff(
                                    PublishHandoffRequest(
                                        songId = currentSong.id,
                                        positionMs = playerState.positionMs,
                                        deviceLabel = deviceLabel
                                    )
                                )
                            }
                            res.onSuccess {
                                snackbar("Estado publicado · puedes continuar en otro móvil")
                                runCatching { container.apiService.listHandoffs() }
                                    .onSuccess { items = it }
                            }.onFailure {
                                snackbar("No se pudo publicar: ${it.message ?: "error"}")
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Upload, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text("Publicar lo que sueno ahora")
                }
            }

            if (loading) {
                Box(Modifier.fillMaxSize(), Alignment.Center) {
                    Text("Cargando…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            if (items.isEmpty()) {
                Box(
                    Modifier.fillMaxSize().padding(24.dp),
                    Alignment.Center
                ) {
                    Text(
                        "Nadie ha publicado estado todavía.\nToca el botón de arriba para empezar.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
                return@Column
            }

            LazyColumn(
                contentPadding = PaddingValues(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                items(items, key = { it.userId }) { h ->
                    HandoffRow(
                        h = h,
                        onTake = {
                            val song = h.song ?: return@HandoffRow
                            container.playerController.playQueue(listOf(song), 0)
                            // Saltamos a la posición publicada tras un tick
                            // para que playQueue haya preparado el media item.
                            scope.launch {
                                kotlinx.coroutines.delay(300)
                                container.playerController.seekTo(h.positionMs)
                                if (h.mine) {
                                    runCatching { container.apiService.clearHandoff() }
                                }
                            }
                            onBack()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun HandoffRow(h: HandoffDto, onTake: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onTake)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        UserAvatar(nick = h.userNick, avatarUrl = h.userAvatarUrl, size = 40.dp)
        Column(
            modifier = Modifier.weight(1f).padding(start = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = h.userNick ?: "?",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (h.mine) {
                    Text(
                        text = "  · este user",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (!h.deviceLabel.isNullOrBlank()) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.DevicesOther,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "  ${h.deviceLabel}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(url = h.song?.coverUrl, size = 32.dp, cornerRadius = 4.dp)
                Column(modifier = Modifier.padding(start = 8.dp).weight(1f)) {
                    Text(
                        text = h.song?.title ?: "(canción desaparecida)",
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        fontWeight = FontWeight.SemiBold
                    )
                    val mmss = "%d:%02d".format(
                        h.positionMs / 60000,
                        (h.positionMs / 1000) % 60
                    )
                    Text(
                        text = "${h.song?.artistsLabel.orEmpty()}  ·  min $mmss",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }
        IconButton(onClick = onTake) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Tomar relevo",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

