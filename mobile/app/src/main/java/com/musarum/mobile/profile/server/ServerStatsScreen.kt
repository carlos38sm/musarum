package com.musarum.mobile.profile.server

import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import kotlinx.coroutines.launch
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.data.network.dto.ServerStatsDto
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerStatsScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: ServerStatsViewModel = viewModel(
        factory = ServerStatsViewModel.factory(container.apiService)
    )
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showWipeDialog by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Análisis del servidor") },
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
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val s = state) {
            is ServerStatsUiState.Loading -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            is ServerStatsUiState.Error -> Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    s.message,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(24.dp)
                )
            }

            is ServerStatsUiState.Ready -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                CatalogCard(s.stats.catalog)
                LyricsCard(s.stats.lyrics)
                DiskCard(s.stats.disk)
                EnrichmentCard(s.stats.enrichment, totalSongs = s.stats.catalog.songs)
                s.stats.emendatium?.let { EmendatiumCard(it) }
                ActivityCard(s.stats.activity)
                TechnicalCard(
                    t = s.stats.technical,
                    onCleanOrphans = { viewModel.cleanOrphanLyrics() }
                )
                MaintenanceCard(
                    songCount = s.stats.catalog.songs,
                    onWipeRequested = { showWipeDialog = true },
                    onReEnrichRequested = {
                        viewModel.reEnrichAll { _, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    onCleanOrphansRequested = {
                        viewModel.cleanOrphanEntities { _, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                )
                Spacer(Modifier.height(8.dp))
            }
        }
    }

    if (showWipeDialog) {
        val songCount = (state as? ServerStatsUiState.Ready)?.stats?.catalog?.songs ?: 0L
        AlertDialog(
            onDismissRequest = { showWipeDialog = false },
            icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
            title = { Text("¿Vaciar la biblioteca?") },
            text = {
                Text(
                    "Se borrarán las $songCount canciones, todos los artistas, " +
                        "álbumes y géneros. También favoritos y entradas de " +
                        "playlists. Las playlists (vacías), usuarios y carpetas " +
                        "configuradas se mantienen. Esta acción no se puede deshacer."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showWipeDialog = false
                        viewModel.wipeLibrary { _, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) { Text("Vaciar") }
            },
            dismissButton = {
                TextButton(onClick = { showWipeDialog = false }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun MaintenanceCard(
    songCount: Long,
    onWipeRequested: () -> Unit,
    onReEnrichRequested: () -> Unit,
    onCleanOrphansRequested: () -> Unit
) {
    StatCard(title = "Mantenimiento") {
        Text(
            "Re-busca biografías de artistas, BPM, tonalidad y replaygain " +
                "en Emendatium. Solo re-encola canciones nunca intentadas " +
                "o con fallos temporales — las que ya se sabe que Emendatium " +
                "no identifica no se repiten.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onReEnrichRequested,
            enabled = songCount > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (songCount > 0) "Re-enriquecer pendientes"
                else "Nada que enriquecer"
            )
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Borra artistas, álbumes y géneros que quedaron sin canciones " +
                "tras un borrado (fantasmas en listas).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onCleanOrphansRequested,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.CleaningServices, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Limpiar huérfanos de catálogo")
        }

        Spacer(Modifier.height(18.dp))
        Text(
            "Vacía el catálogo musical del servidor cuando vayas a cargar " +
                "una carpeta de música nueva. No toca usuarios, playlists ni " +
                "las carpetas configuradas.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = onWipeRequested,
            enabled = songCount > 0,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.DeleteSweep, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (songCount > 0) "Vaciar biblioteca ($songCount canciones)"
                else "Biblioteca ya vacía"
            )
        }
    }
}

/* ─────────────────────────── Tarjetas por sección ──────────────────────── */

@Composable
private fun CatalogCard(c: ServerStatsDto.CatalogStats) {
    StatCard(title = "Catálogo") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatBig(label = "Canciones", value = c.songs.toString(), modifier = Modifier.weight(1f))
            StatBig(label = "Artistas", value = c.artists.toString(), modifier = Modifier.weight(1f))
            StatBig(label = "Álbumes", value = c.albums.toString(), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        StatLine("Géneros", c.genres.toString())
        StatLine("Playlists", c.playlists.toString())
        StatLine("Favoritos totales", c.totalFavorites.toString())
        val usersSuffix = if (c.blockedUsers > 0) " · ${c.blockedUsers} bloqueados" else ""
        StatLine("Usuarios", "${c.users}$usersSuffix")
    }
}

@Composable
private fun LyricsCard(l: ServerStatsDto.LyricsStats) {
    StatCard(title = "Letras") {
        val pct = (l.coveragePercent / 100.0).coerceIn(0.0, 1.0).toFloat()
        LinearProgressIndicator(
            progress = { pct },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "%.1f%% con letra".format(l.coveragePercent),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(6.dp))
        StatLine("Con letra", l.withLyrics.toString())
        StatLine("Sin letra", l.withoutLyrics.toString())
        StatLine("Sincronizadas", l.syncedLyrics.toString())
        StatLine("Solo texto", l.plainLyrics.toString())
    }
}

@Composable
private fun DiskCard(d: ServerStatsDto.DiskStats) {
    StatCard(title = "Disco") {
        if (d.diskTotalBytes > 0) {
            val used = d.diskTotalBytes - d.diskFreeBytes
            val pct = used.toFloat() / d.diskTotalBytes.toFloat()
            LinearProgressIndicator(
                progress = { pct.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = MaterialTheme.colorScheme.tertiary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "${humanBytes(used)} usados · ${humanBytes(d.diskFreeBytes)} libres de ${humanBytes(d.diskTotalBytes)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
        }
        StatLine("Música", humanBytes(d.musicBytes))
        StatLine("Portadas", humanBytes(d.coversBytes))
        StatLine("Letras", humanBytes(d.lyricsBytes))
        StatLine("Vídeos", humanBytes(d.videosBytes))
        StatLine("Avatares", humanBytes(d.avatarsBytes))
        StatLine("Base de datos", humanBytes(d.databaseBytes))
    }
}

@Composable
private fun EnrichmentCard(e: ServerStatsDto.EnrichmentStats, totalSongs: Long) {
    StatCard(title = "Pendiente de enriquecer") {
        Text(
            "Canciones a las que Emendatium aún no ha dado un valor.",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        StatLine("Sin portada", "${e.missingCover} / $totalSongs")
        StatLine("Sin BPM", "${e.missingBpm} / $totalSongs")
        StatLine("Sin tonalidad", "${e.missingKey} / $totalSongs")
        StatLine("Sin ReplayGain", "${e.missingReplayGain} / $totalSongs")
        StatLine("Sin MusicBrainz ID", "${e.missingMbid} / $totalSongs")

        // Desglose por status del intento Emendatium (si el backend lo trae).
        // Útil para diagnosticar: si ok=0 aunque Emendatium esté verde →
        // está respondiendo pero sin metadata (fpcalc no instalado, etc.).
        val pending = e.enrichPending
        val ok = e.enrichOk
        val noMatch = e.enrichNoMatch
        val transient = e.enrichTransient
        if (pending != null && ok != null) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Último intento Emendatium",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(6.dp))
            StatLine("Enriquecidas", ok.toString())
            StatLine("Nunca intentadas", pending.toString())
            if ((noMatch ?: 0) > 0) StatLine("Sin match", noMatch.toString())
            if ((transient ?: 0) > 0) StatLine("Fallo temporal", transient.toString())
        }
    }
}

@Composable
private fun EmendatiumCard(e: ServerStatsDto.EmendatiumStats) {
    val titleIcon = when {
        !e.enabled -> "⚪"   // No configurado
        e.reachable -> "🟢"   // Vivo
        else -> "🔴"          // Caído
    }
    StatCard(title = "Emendatium $titleIcon") {
        val lineColor = when {
            !e.enabled -> MaterialTheme.colorScheme.onSurfaceVariant
            e.reachable -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.error
        }
        val statusText = when {
            !e.enabled -> "No configurado · sin él, el catálogo no recibe BPM, tonalidad, bio de artista ni replaygain."
            e.reachable -> "Alcanzable · enriqueciendo canciones a medida que llegan."
            else -> "Configurado pero NO responde. Mientras siga así, BPM/tonalidad/bio no van a aparecer aunque re-escanees."
        }
        Text(
            statusText,
            style = MaterialTheme.typography.bodySmall,
            color = lineColor
        )
        Spacer(Modifier.height(10.dp))
        e.baseUrl?.let { StatLine("URL", it) }
        if (e.enabled && !e.reachable && !e.message.isNullOrBlank()) {
            StatLine("Motivo", e.message)
        }
        e.lastCheckedAtMs?.let { StatLine("Última comprobación", humanInstant(it)) }
    }
}

@Composable
private fun ActivityCard(a: ServerStatsDto.ActivityStats) {
    StatCard(title = "Actividad") {
        Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            StatBig(label = "Total", value = a.totalPlays.toString(), modifier = Modifier.weight(1f))
            StatBig(label = "7 días", value = a.playsLast7Days.toString(), modifier = Modifier.weight(1f))
            StatBig(label = "30 días", value = a.playsLast30Days.toString(), modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        StatLine("Usuarios activos (30d)", a.activeUsersLast30Days.toString())
        StatLine("Última reproducción", humanInstant(a.lastPlayAt))
        StatLine("Último escaneo", humanInstant(a.lastScanAt))
        a.lastScanSummary?.let { StatLine("Resumen escaneo", it) }
    }
}

@Composable
private fun TechnicalCard(
    t: ServerStatsDto.TechnicalStats,
    onCleanOrphans: () -> Unit = {}
) {
    StatCard(title = "Técnico") {
        StatLine("Uptime", humanDuration(t.uptimeMs))
        StatLine("Memoria usada", humanBytes(t.memoryUsedBytes))
        StatLine("Memoria asignada", humanBytes(t.memoryTotalBytes))
        StatLine("Memoria máxima", humanBytes(t.memoryMaxBytes))
        if (t.orphanLyricFiles > 0) {
            StatLine("Letras huérfanas", t.orphanLyricFiles.toString())
            Spacer(Modifier.height(6.dp))
            androidx.compose.material3.TextButton(onClick = onCleanOrphans) {
                Text("Limpiar ${t.orphanLyricFiles} ficheros .lrc huérfanos")
            }
        }
        val errors = t.recentScanErrors.orEmpty()
        if (errors.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            androidx.compose.material3.HorizontalDivider()
            Spacer(Modifier.height(10.dp))
            Text(
                "Errores del último scan (últimos ${errors.size})",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(6.dp))
            // Mostramos los 10 más recientes; el usuario cuenta con el total
            // en la cabecera y puede abrir logs del contenedor para el resto.
            errors.takeLast(10).reversed().forEach { line ->
                Text(
                    "• $line",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 1.dp)
                )
            }
        }
    }
}

/* ─────────────────────────── Componentes base ──────────────────────────── */

@Composable
private fun StatCard(title: String, content: @Composable () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun StatBig(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            value,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun StatLine(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

/* ─────────────────────────── Formatos ──────────────────────────────────── */

internal fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(Locale.getDefault(), kb)
    val mb = kb / 1024.0
    if (mb < 1024) return "%.1f MB".format(Locale.getDefault(), mb)
    val gb = mb / 1024.0
    if (gb < 1024) return "%.2f GB".format(Locale.getDefault(), gb)
    val tb = gb / 1024.0
    return "%.2f TB".format(Locale.getDefault(), tb)
}

internal fun humanDuration(ms: Long): String {
    val d = Duration.ofMillis(ms)
    val days = d.toDays()
    val hours = d.toHours() % 24
    val minutes = d.toMinutes() % 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${minutes}m"
        minutes > 0 -> "${minutes}m"
        else -> "${d.seconds}s"
    }
}

internal fun humanInstant(ms: Long?): String {
    if (ms == null) return "—"
    val fmt = DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm").withZone(ZoneId.systemDefault())
    return fmt.format(Instant.ofEpochMilli(ms))
}
