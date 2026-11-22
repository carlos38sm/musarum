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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.produceState
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.music.CoverImage
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.data.network.dto.SongDetailsDto
import com.musarum.mobile.music.library.AddToPlaylistDialog
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton

@Composable
fun InfoScreen(
    onBack: () -> Unit,
    onOpenTrack: () -> Unit,
    onOpenLyrics: () -> Unit,
    onOpenVideo: () -> Unit
) {
    val container = LocalAppContainer.current
    val controller = container.playerController
    val playerState by controller.state.collectAsState()

    val viewModel: NowPlayingViewModel = viewModel(
        factory = NowPlayingViewModel.factory(
            container.songRepository,
            container.favoriteRepository,
            controller
        )
    )
    val vmState by viewModel.state.collectAsState()

    val song = playerState.currentSong
    if (song == null) {
        onBack()
        return
    }


    PlayerShellScaffold(
        selectedMode = PlayerMode.INFO,
        onSelectMode = { mode ->
            when (mode) {
                PlayerMode.TRACK -> onOpenTrack()
                PlayerMode.LYRICS -> onOpenLyrics()
                PlayerMode.INFO -> Unit
                PlayerMode.VIDEO -> onOpenVideo()
            }
        },
        showVideo = vmState.details?.hasVideo == true,
        onBack = onBack,
        isFavorite = false,
        onToggleFavorite = {},
        onAddToPlaylist = {},
        topActionsVisible = false,
        // Orden lógico: LYRICS · VIDEO · TRACK · INFO. Desde INFO,
        // deslizar a la derecha "va hacia atrás" en la pastilla →
        // la pestaña anterior es TRACK (Audio). A la izquierda ya no
        // hay más — lo dejamos no-op.
        onSwipeRight = onOpenTrack
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )
            Text(
                song.artistsLabel,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }

        Spacer(Modifier.height(20.dp))

        val me by container.userRepository.me.collectAsState()
        val snackbar = LocalSnackbar.current
        var showEditTags by remember { mutableStateOf(false) }

        Box(modifier = Modifier.fillMaxSize().padding(horizontal = 24.dp)) {
            when {
                vmState.detailsLoading -> Center {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
                vmState.detailsError != null -> Center {
                    Text(vmState.detailsError ?: "", color = MaterialTheme.colorScheme.error)
                }
                vmState.details != null -> InfoSections(
                    details = vmState.details!!,
                    showEditButton = me?.userRole == "OWNER",
                    isOwner = me?.userRole == "OWNER",
                    onEdit = { showEditTags = true }
                )
            }
        }

        if (showEditTags && vmState.details != null) {
            EditTagsDialog(
                details = vmState.details!!,
                onDismiss = { showEditTags = false },
                onSave = { req ->
                    viewModel.editTags(req) { ok, msg ->
                        if (ok) {
                            showEditTags = false
                            snackbar(msg)
                        } else {
                            snackbar(msg)
                        }
                    }
                }
            )
        }
    }

}

/**
 * Vista nueva en secciones. Los campos CORTOS se reparten en pares (2
 * columnas al 50%) para aprovechar el ancho; los LARGOS (título, ruta,
 * álbum, compositor, identificadores) siguen a ancho completo.
 */
@Composable
private fun InfoSections(
    details: SongDetailsDto,
    showEditButton: Boolean = false,
    isOwner: Boolean = false,
    onEdit: () -> Unit = {}
) {
    val camelot = CamelotWheel.toCamelot(details.musicalKey)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // ── Metadata ──
        Section(
            title = "Metadata",
            action = if (showEditButton) {
                { IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "Editar tags",
                        tint = MaterialTheme.colorScheme.primary
                    )
                } }
            } else null
        ) {
            InfoLine("Título", details.title)
            if (details.artists.isNotEmpty()) {
                InfoLine("Artistas", details.artists.joinToString(", ") { it.name })
            }
            details.albumTitle?.let { InfoLine("Álbum", it) }
            details.composer?.let { InfoLine("Compositor", it) }

            val year = details.year ?: details.albumYear
            PairRow(
                left = details.genreName?.let { "Género" to it },
                right = year?.let { "Año" to it.toString() }
            )
            val trackValue = details.trackNumber?.let { t ->
                details.discNumber?.let { d -> "$t · disco $d" } ?: t.toString()
            }
            PairRow(
                left = trackValue?.let { "Nº de pista" to it },
                right = details.durationMs?.let { "Duración" to formatMs(it.toLong()) }
            )
        }

        // ── Musical ──
        val hasMusical = details.bpm != null || details.musicalKey != null || details.replayGain != null
        if (hasMusical) {
            Section("Musical") {
                // Primera fila: BPM + Tonalidad musical
                PairRow(
                    left = details.bpm?.let { "BPM" to it.toString() },
                    right = details.musicalKey?.let { "Tonalidad" to it }
                )
                // Segunda fila: Camelot (código DJ) + ReplayGain
                PairRow(
                    left = camelot?.let { "Camelot" to it },
                    right = details.replayGain?.let { "ReplayGain" to "%.2f dB".format(it) }
                )
                // Pequeña aclaración de qué es ReplayGain, en la propia celda
                details.replayGain?.let {
                    Text(
                        "ReplayGain es el ajuste de volumen aplicado al reproducir " +
                                "para igualar el nivel con otras canciones (negativo = atenúa, positivo = amplifica).",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        // ── Técnico ──
        val hasTech = details.format != null || details.bitrate != null
                || details.sampleRate != null || details.channels != null
                || details.fileSize != null || details.audioPath != null
                || isOwner
        if (hasTech) {
            Section("Técnico") {
                PairRow(
                    left = details.format?.let { "Formato" to it },
                    right = details.bitrate?.let { "Bitrate" to "$it kbps" }
                )
                PairRow(
                    left = details.sampleRate?.let { "Muestreo" to formatSampleRate(it) },
                    right = details.channels?.let { "Canales" to formatChannels(it) }
                )
                PairRow(
                    left = details.fileSize?.let { "Tamaño" to formatFileSize(it) },
                    right = null
                )
                details.audioPath?.let { InfoLine("Ruta del archivo", it) }
                // ID visible solo para el OWNER: útil para consultas directas
                // en la DB cuando una canción se porta mal (letras raras,
                // metadata inconsistente). No aporta nada al resto de users.
                if (isOwner) {
                    InfoLine("ID canción", details.id.toString())
                }
            }
        }

        // ── Identificadores ──
        val hasIds = details.isrc != null || details.mbId != null
        if (hasIds) {
            Section("Identificadores") {
                details.isrc?.let { InfoLine("ISRC", it) }
                details.mbId?.let { InfoLine("MusicBrainz", it) }
            }
        }

        // ── Esta canción suena a…
        // Carga bajo demanda cuando la pantalla es visible. El backend
        // devuelve lista vacía si no hay BPM/tonalidad en la fuente
        // (Emendatium no ha pasado) y aquí ocultamos la sección entera
        // sin ruido. produceState se rearranca al cambiar details.id,
        // así que si el user pasa de canción el bloque se refresca.
        val container = LocalAppContainer.current
        val similarState = produceState<List<SongSummaryDto>>(
            initialValue = emptyList(),
            key1 = details.id
        ) {
            value = runCatching {
                container.apiService.getSimilarSongs(details.id, 5)
            }.getOrDefault(emptyList())
        }
        val similar = similarState.value
        if (similar.isNotEmpty()) {
            Section("Esta canción suena a…") {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(similar) { s ->
                        SimilarCard(
                            song = s,
                            onClick = {
                                val idx = similar.indexOfFirst { it.id == s.id }.coerceAtLeast(0)
                                container.playerController.playQueue(similar, idx)
                            }
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(32.dp))
    }
}

/**
 * Tarjeta mini del carrusel "Esta canción suena a…": cover 100dp +
 * título y artista debajo, ancho fijo para que entren ~3 en pantalla.
 * Toque = reproduce la cola de similares empezando por ésta.
 */
@Composable
private fun SimilarCard(song: SongSummaryDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(104.dp)
            .clickable(onClick = onClick)
    ) {
        CoverImage(url = song.coverUrl, size = 100.dp, cornerRadius = 12.dp)
        Spacer(Modifier.height(6.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            song.artistsLabel,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Cada sección se dibuja como una tarjeta glass con esquinas redondeadas
 * para que el ojo las separe claramente — era un bloque corrido antes.
 */
@Composable
private fun Section(
    title: String,
    action: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                title.uppercase(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 4.dp, bottom = 2.dp)
            )
            Spacer(Modifier.weight(1f))
            action?.invoke()
        }
        androidx.compose.material3.Surface(
            shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun InfoLine(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/**
 * Fila con dos columnas al 50%. Si solo viene uno de los dos lados, se
 * dibuja ocupando su columna sin estirarse a ancho completo (así siempre
 * queda alineado con las parejas vecinas).
 */
@Composable
private fun PairRow(
    left: Pair<String, String>?,
    right: Pair<String, String>?
) {
    if (left == null && right == null) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(modifier = Modifier.weight(1f)) {
            left?.let { (l, v) -> MiniCell(l, v) }
        }
        Box(modifier = Modifier.weight(1f)) {
            right?.let { (l, v) -> MiniCell(l, v) }
        }
    }
}

@Composable
private fun MiniCell(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            value,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun formatSampleRate(hz: Int): String {
    val khz = hz / 1000.0
    return if (khz == khz.toInt().toDouble()) "${khz.toInt()} kHz"
    else "%.1f kHz".format(khz)
}

private fun formatChannels(c: Int): String = when (c) {
    1 -> "Mono"
    2 -> "Estéreo"
    else -> "$c canales"
}

private fun formatFileSize(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 1.0) "%.2f MB".format(mb) else "%.0f KB".format(bytes / 1024.0)
}

@Composable
private fun Center(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), Alignment.Center) { content() }
}
