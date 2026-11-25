package com.musarum.mobile.party

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.musarum.mobile.data.network.dto.PartyDto
import com.musarum.mobile.data.network.dto.PartyQueueItemDto
import com.musarum.mobile.data.network.dto.SetPartyCurrentRequest
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.music.UserAvatar
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Fiesta musical: una cola compartida entre los users de la casa. Hay
 * UNA party activa a la vez en el backend.
 *   - Si NO hay party: botón "Crear fiesta" (yo seré host).
 *   - Si HAY party: lista de cola + canción actual + miembros.
 *     Host: puede iniciar la siguiente, cerrar.
 *     Miembro: puede añadir canciones y salir.
 *
 * Polling cada 4 segundos para enterarse de añadidos / cambios del host.
 * Sin WebSocket — es suficiente para 4 users en Tailscale.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PartyScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    var party by remember { mutableStateOf<PartyDto?>(null) }
    var loading by remember { mutableStateOf(true) }
    val playerState by container.playerController.state.collectAsStateWithLifecycle()

    suspend fun refresh() {
        val res = runCatching { container.apiService.getActiveParty() }.getOrNull()
        party = if (res != null && res.isSuccessful) res.body() else null
    }

    // Poll inicial + cada 4s mientras la pantalla esté montada.
    LaunchedEffect(Unit) {
        refresh()
        loading = false
        while (true) {
            delay(4000)
            refresh()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Fiesta musical") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { inner ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(inner), Alignment.Center) {
                CircularProgressIndicator()
            }
            party == null -> NoActiveParty(
                modifier = Modifier.fillMaxSize().padding(inner),
                onCreate = {
                    scope.launch {
                        runCatching { container.apiService.createParty() }
                            .onSuccess {
                                party = it
                                snackbar("Fiesta creada · eres el host")
                            }
                            .onFailure { snackbar("No se pudo crear la fiesta") }
                    }
                }
            )
            else -> ActivePartyView(
                party = party!!,
                modifier = Modifier.fillMaxSize().padding(inner),
                onAddCurrent = {
                    val song = playerState.currentSong
                    if (song == null) {
                        snackbar("No hay canción sonando para añadir")
                        return@ActivePartyView
                    }
                    scope.launch {
                        runCatching {
                            container.apiService.addSongToParty(
                                party!!.id,
                                com.musarum.mobile.data.network.dto.AddPartySongRequest(song.id)
                            )
                        }.onSuccess { snackbar("Añadida a la cola") }
                            .onFailure { snackbar("No se pudo añadir") }
                        refresh()
                    }
                },
                onPlayQueueItem = { item ->
                    val song = item.song ?: return@ActivePartyView
                    // Si eres host, actualiza el "current" del backend para
                    // que el resto vea qué suena. Si no, solo lo reproduces
                    // en local — eres "escuchante" de la party.
                    container.playerController.playQueue(listOf(song), 0)
                    if (party!!.iAmHost) {
                        scope.launch {
                            runCatching {
                                container.apiService.setPartyCurrent(
                                    party!!.id,
                                    SetPartyCurrentRequest(song.id)
                                )
                            }
                            refresh()
                        }
                    }
                },
                onLeave = {
                    val pid = party!!.id
                    scope.launch {
                        runCatching { container.apiService.leaveParty(pid) }
                        refresh()
                        snackbar("Has salido de la fiesta")
                    }
                },
                onClose = {
                    val pid = party!!.id
                    scope.launch {
                        runCatching { container.apiService.closeParty(pid) }
                        party = null
                        snackbar("Fiesta cerrada")
                    }
                }
            )
        }
    }
}

@Composable
private fun NoActiveParty(modifier: Modifier, onCreate: () -> Unit) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            Icons.Filled.Celebration,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "No hay fiesta activa ahora",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "Crea una y los demás verán una cola compartida en vivo. " +
                "Todos pueden añadir canciones al final. Tú (host) decides " +
                "qué suena ahora.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        Spacer(Modifier.height(20.dp))
        Button(onClick = onCreate) {
            Icon(Icons.Filled.Celebration, contentDescription = null)
            Spacer(Modifier.size(8.dp))
            Text("Crear fiesta")
        }
    }
}

@Composable
private fun ActivePartyView(
    party: PartyDto,
    modifier: Modifier,
    onAddCurrent: () -> Unit,
    onPlayQueueItem: (PartyQueueItemDto) -> Unit,
    onLeave: () -> Unit,
    onClose: () -> Unit
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "Fiesta de ${party.hostNick ?: "—"}${if (party.iAmHost) " (tú)" else ""}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    val cs = party.currentSong
                    if (cs != null) {
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CoverImage(url = cs.coverUrl, size = 48.dp, cornerRadius = 8.dp)
                            Column(modifier = Modifier.padding(start = 12.dp).fillMaxWidth()) {
                                Text(
                                    "Ahora sonando",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    cs.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    cs.artistsLabel,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Miembros como bolitas pequeñas.
                        party.members.take(5).forEach { m ->
                            UserAvatar(nick = m.nick, avatarUrl = m.avatarUrl, size = 28.dp)
                        }
                        if (party.members.size > 5) {
                            Text(
                                "+${party.members.size - 5}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = onAddCurrent,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(6.dp))
                    Text("Añadir la actual")
                }
                if (party.iAmHost) {
                    OutlinedButton(
                        onClick = onClose,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Stop, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Cerrar")
                    }
                } else {
                    OutlinedButton(
                        onClick = onLeave,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.ExitToApp, contentDescription = null)
                        Spacer(Modifier.size(6.dp))
                        Text("Salir")
                    }
                }
            }
        }

        if (party.queue.isEmpty()) {
            item {
                Text(
                    "La cola está vacía. Añade canciones desde aquí o desde el long-press en cualquier lista.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 16.dp)
                )
            }
        } else {
            items(party.queue, key = { it.id }) { item ->
                QueueRow(item = item, onPlay = { onPlayQueueItem(item) })
            }
        }
    }
}

@Composable
private fun QueueRow(item: PartyQueueItemDto, onPlay: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
    ) {
        Text(
            text = "${item.position + 1}.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(end = 8.dp)
                .width(28.dp)
        )
        CoverImage(url = item.song?.coverUrl, size = 40.dp, cornerRadius = 6.dp)
        Column(
            modifier = Modifier.weight(1f).padding(start = 10.dp)
        ) {
            Text(
                item.song?.title ?: "(canción desaparecida)",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
            Text(
                "${item.song?.artistsLabel.orEmpty()}  ·  añadida por ${item.addedByNick ?: "?"}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        IconButton(onClick = onPlay) {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Reproducir",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

