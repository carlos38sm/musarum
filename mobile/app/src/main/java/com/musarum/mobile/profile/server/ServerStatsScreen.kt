package com.musarum.mobile.profile.server

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Switch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.musarum.mobile.data.network.dto.HostStatsDto
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
    val state by viewModel.state.collectAsStateWithLifecycle()
    val host by viewModel.host.collectAsStateWithLifecycle()
    val hostLive by viewModel.hostLive.collectAsStateWithLifecycle()
    val hostHistory by viewModel.hostHistory.collectAsStateWithLifecycle()
    val enrichProgress by viewModel.enrichProgress.collectAsStateWithLifecycle()
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
                MachineCard(
                    host = host,
                    history = hostHistory,
                    live = hostLive,
                    onLiveChange = { viewModel.setHostLive(it) }
                )
                MaintenanceCard(
                    songCount = s.stats.catalog.songs,
                    artistCount = s.stats.catalog.artists,
                    progress = enrichProgress,
                    onDismissProgress = { viewModel.dismissEnrichProgress() },
                    onCancelEnrich = {
                        viewModel.cancelEnrichment { _, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    onWipeRequested = { showWipeDialog = true },
                    onReEnrichRequested = {
                        viewModel.reEnrichAll { _, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    onReEnrichArtistsRequested = {
                        viewModel.reEnrichArtists { _, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    onReEnrichSongsOnlyRequested = {
                        viewModel.reEnrichSongsOnly { _, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    },
                    onCleanOrphansRequested = {
                        viewModel.cleanOrphanEntities { _, msg ->
                            scope.launch { snackbarHostState.showSnackbar(msg) }
                        }
                    }
                )
                BackupsCard(
                    onSnackbar = { msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                )
                ObservabilityCard(baseUrl = container.baseUrl)
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

/**
 * Botón directo a Grafana. La URL se deriva de la baseUrl del backend
 * reemplazando el puerto 8080 por 3000 y quitando el context-path; si el
 * OWNER no ha levantado el profile 'obs' del compose, Android abrirá la
 * página de error del navegador — es mejor que un botón que no explique.
 */
@Composable
private fun ObservabilityCard(baseUrl: String) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val grafanaUrl = remember(baseUrl) { deriveGrafanaUrl(baseUrl) }
    StatCard(title = "Observabilidad") {
        Text(
            "Prometheus scrapea /actuator/prometheus del backend cada 30 s " +
                "y Grafana lo pinta. Levanta ambos con `docker compose " +
                "--profile obs up -d` en el servidor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                val intent = android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse(grafanaUrl)
                ).apply { addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK) }
                runCatching { ctx.startActivity(intent) }
            },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Insights, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text("Abrir Grafana")
        }
        Spacer(Modifier.height(6.dp))
        Text(
            grafanaUrl,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

private fun deriveGrafanaUrl(baseUrl: String): String {
    // baseUrl típico: http://100.88.167.4:8080/api/v1/ → http://100.88.167.4:3000/
    return runCatching {
        val u = java.net.URI(baseUrl)
        val scheme = u.scheme ?: "http"
        val host = u.host ?: "localhost"
        "$scheme://$host:3000/"
    }.getOrDefault("http://localhost:3000/")
}

@Composable
private fun MaintenanceCard(
    songCount: Long,
    artistCount: Long,
    progress: com.musarum.mobile.data.network.dto.EnrichProgressDto?,
    onDismissProgress: () -> Unit,
    onCancelEnrich: () -> Unit,
    onWipeRequested: () -> Unit,
    onReEnrichRequested: () -> Unit,
    onReEnrichArtistsRequested: () -> Unit,
    onReEnrichSongsOnlyRequested: () -> Unit,
    onCleanOrphansRequested: () -> Unit
) {
    StatCard(title = "Mantenimiento") {
        Text(
            "Vuelve a pasar Emendatium por el catálogo. Elige qué refrescar: " +
                "todo a la vez, solo artistas (fotos + bio, es lo más rápido) " +
                "o solo canciones (BPM, tonalidad, letras, ReplayGain).",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Barra de progreso del run activo (o del último si terminó).
        EnrichProgressBlock(
            progress = progress,
            onDismiss = onDismissProgress,
            onCancel = onCancelEnrich
        )

        Spacer(Modifier.height(12.dp))
        val running = progress?.running == true
        Button(
            onClick = onReEnrichRequested,
            enabled = songCount > 0 && !running,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.AutoFixHigh, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                when {
                    songCount <= 0 -> "Nada que enriquecer"
                    running -> "Enriqueciendo…"
                    else -> "Re-enriquecer todo ($songCount canciones)"
                }
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onReEnrichArtistsRequested,
            enabled = artistCount > 0 && !running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Person, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (artistCount > 0) "Re-enriquecer artistas ($artistCount)"
                else "Sin artistas que refrescar"
            )
        }

        Spacer(Modifier.height(8.dp))
        OutlinedButton(
            onClick = onReEnrichSongsOnlyRequested,
            enabled = songCount > 0 && !running,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.MusicNote, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(
                if (songCount > 0) "Re-enriquecer canciones ($songCount)"
                else "Sin canciones que refrescar"
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

@Composable
private fun BackupsCard(onSnackbar: (String) -> Unit) {
    val container = LocalAppContainer.current
    val scope = rememberCoroutineScope()
    var items by remember { mutableStateOf<List<com.musarum.mobile.data.network.dto.BackupFileDto>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var creating by remember { mutableStateOf(false) }

    androidx.compose.runtime.LaunchedEffect(Unit) {
        runCatching { container.apiService.listBackups() }
            .onSuccess { items = it }
        loading = false
    }

    StatCard(title = "Backups automáticos") {
        Text(
            "El servidor hace backup de playlists y favoritos cada 30 minutos " +
                "en _backups/. Mantiene los últimos 10 y rota el resto.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Button(
            onClick = {
                if (creating) return@Button
                creating = true
                scope.launch {
                    val res = runCatching { container.apiService.createBackup() }
                    creating = false
                    res.onSuccess { nuevo ->
                        items = (listOf(nuevo) + items.filter { it.fileName != nuevo.fileName })
                            .take(10)
                        onSnackbar("Backup creado: ${nuevo.fileName}")
                    }.onFailure {
                        onSnackbar("No se pudo hacer el backup: ${it.message ?: "error"}")
                    }
                }
            },
            enabled = !creating,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Save, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (creating) "Creando…" else "Hacer backup ahora")
        }

        Spacer(Modifier.height(12.dp))

        if (loading) {
            Text(
                "Cargando lista…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else if (items.isEmpty()) {
            Text(
                "Aún no hay backups guardados.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            items.forEach { b ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            b.fileName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            humanBytes(b.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/* â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ Tarjetas por sección â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */

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
        // Útil para diagnosticar: si ok=0 aunque Emendatium esté verde â†’
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
        !e.enabled -> "âšª"   // No configurado
        e.reachable -> "ðŸŸ¢"   // Vivo
        else -> "ðŸ”´"          // Caído
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

/* â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ Barra de progreso del re-enrich â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */

@Composable
private fun EnrichProgressBlock(
    progress: com.musarum.mobile.data.network.dto.EnrichProgressDto?,
    onDismiss: () -> Unit,
    onCancel: () -> Unit
) {
    if (progress == null || progress.total <= 0L) return

    val pct = (progress.processed.toFloat() / progress.total.toFloat()).coerceIn(0f, 1f)
    val elapsedMs = (progress.finishedAtMs ?: System.currentTimeMillis()) -
            (progress.startedAtMs ?: System.currentTimeMillis())
    val etaMs = if (progress.running && progress.processed > 0) {
        val rate = progress.processed.toDouble() / elapsedMs.toDouble()  // canciones/ms
        if (rate > 0) ((progress.total - progress.processed) / rate).toLong() else 0L
    } else 0L

    Spacer(Modifier.height(14.dp))
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            if (progress.running) "Enriqueciendo canciones…"
            else "Enriquecido · ${progress.succeeded} ok / ${progress.failed} fallidas",
            style = MaterialTheme.typography.labelMedium,
            color = if (progress.running) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        if (progress.running) {
            androidx.compose.material3.TextButton(onClick = onCancel) {
                Text("Cancelar", color = MaterialTheme.colorScheme.error)
            }
        } else {
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text("Descartar")
            }
        }
    }
    Spacer(Modifier.height(6.dp))
    LinearProgressIndicator(
        progress = { pct },
        modifier = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(50)),
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant
    )
    Spacer(Modifier.height(6.dp))
    val subLine = buildString {
        append("${progress.processed} / ${progress.total}")
        append(" · %.0f%%".format(pct * 100))
        if (progress.running && etaMs > 0) {
            append(" · quedan ~")
            append(humanDuration(etaMs))
        }
        if (!progress.running && progress.startedAtMs != null && progress.finishedAtMs != null) {
            append(" · tardó ")
            append(humanDuration(progress.finishedAtMs - progress.startedAtMs))
        }
    }
    Text(
        subLine,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/* â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ Tarjeta Máquina â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */

@Composable
private fun MachineCard(
    host: HostStatsDto?,
    history: com.musarum.mobile.data.network.dto.HostHistoryDto?,
    live: Boolean,
    onLiveChange: (Boolean) -> Unit
) {
    StatCard(title = "Máquina") {
        // Cabecera con toggle live.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Estado del Windows del servidor",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    if (live) "Refresco en vivo (cada 2s)"
                    else "Pulsa para refrescar en vivo",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (live) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = live, onCheckedChange = onLiveChange)
        }

        if (host == null) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Cargando métricas del sistema… (o el backend no las expone todavía)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@StatCard
        }

        // SO + CPU model
        Spacer(Modifier.height(8.dp))
        Text(
            host.cpuModel,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            host.osName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // CPU bar
        Spacer(Modifier.height(14.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Memory,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(18.dp).height(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                "CPU · ${host.cpuThreads} hilos",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                "%.0f%%".format(host.cpuLoadSystem * 100),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { host.cpuLoadSystem.toFloat().coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        // Sparkline histórica (30 min). Sin backend viejo o buffer vacío
        // no pinta nada — deja hueco controlado para que el salto no sea
        // brusco cuando entre la primera muestra.
        history?.let {
            Spacer(Modifier.height(6.dp))
            Sparkline(
                values = it.samples.map { s -> s.cpuLoadSystem.toFloat().coerceIn(0f, 1f) },
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.fillMaxWidth().height(36.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Proceso Musarum: %.0f%%".format(host.cpuLoadProcess * 100),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // RAM bar
        Spacer(Modifier.height(14.dp))
        val memPct = if (host.memTotalBytes > 0)
            host.memUsedBytes.toFloat() / host.memTotalBytes.toFloat()
        else 0f
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Filled.Storage,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.width(18.dp).height(18.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text("RAM", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            Text(
                "%.0f%%".format(memPct * 100),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { memPct.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.tertiary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        history?.let {
            Spacer(Modifier.height(6.dp))
            Sparkline(
                values = it.samples.map { s ->
                    if (s.memTotalBytes > 0)
                        (s.memUsedBytes.toFloat() / s.memTotalBytes.toFloat()).coerceIn(0f, 1f)
                    else 0f
                },
                color = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.fillMaxWidth().height(36.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "${humanBytes(host.memUsedBytes)} de ${humanBytes(host.memTotalBytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // Swap (si hay)
        if (host.swapTotalBytes > 0) {
            Spacer(Modifier.height(6.dp))
            StatLine(
                "Swap",
                "${humanBytes(host.swapUsedBytes)} / ${humanBytes(host.swapTotalBytes)}"
            )
        }

        // Uptime de la máquina + hilos JVM
        Spacer(Modifier.height(6.dp))
        StatLine("Uptime del PC", humanDuration(host.osUptimeSeconds * 1000L))
        StatLine("Hilos JVM", host.jvmThreadCount.toString())

        // Red
        if (host.networkInterfaces.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.NetworkCheck,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.width(18.dp).height(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    "Red",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            // Sparkline agregada rx+tx. Se normaliza al máximo del propio
            // buffer: así visualmente siempre hay contraste, sin importar
            // que la red del portátil tope en 50 KB/s o en 50 MB/s.
            history?.let {
                val totals = it.samples.map { s -> (s.rxBytesPerSec + s.txBytesPerSec).toFloat() }
                val max = (totals.maxOrNull() ?: 0f).coerceAtLeast(1f)
                val norm = totals.map { v -> (v / max).coerceIn(0f, 1f) }
                Spacer(Modifier.height(6.dp))
                Sparkline(
                    values = norm,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth().height(36.dp)
                )
            }
            Spacer(Modifier.height(6.dp))
            host.networkInterfaces.forEach { nif ->
                NetworkInterfaceRow(nif)
            }
        }

        // Disco IO (si el host lo da)
        if (host.diskIo.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text(
                "Discos",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            host.diskIo.forEach { d ->
                Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                    Text(
                        d.model?.ifBlank { d.name } ?: d.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        "${humanBytes(d.sizeBytes)} · â†“ ${humanBytesPerSec(d.readBytesPerSec)} · â†‘ ${humanBytesPerSec(d.writeBytesPerSec)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NetworkInterfaceRow(nif: HostStatsDto.NetworkInterfaceDto) {
    val color = if (nif.up) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.onSurfaceVariant
    Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                nif.displayName.ifBlank { nif.name },
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = color,
                modifier = Modifier.weight(1f)
            )
            Text(
                "â†“ ${humanBytesPerSec(nif.rxBytesPerSec)}  â†‘ ${humanBytesPerSec(nif.txBytesPerSec)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        val sub = buildString {
            nif.ipv4?.let { append(it) }
            if (!nif.up) {
                if (isNotEmpty()) append(" · ")
                append("caído")
            }
        }
        if (sub.isNotBlank()) {
            Text(
                sub,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Mini-gráfica de los últimos N valores normalizados 0..1. Se dibuja
 * una línea con el color dado + relleno semi-transparente debajo.
 * Si hay menos de 2 muestras no pinta nada (dejaría líneas/puntos
 * sueltos que confunden más que ayudan).
 *
 * Nota: la escala de X es "una muestra por paso", no por tiempo real.
 * Eso vale porque el backend muestrea a tick fijo (10s); si saltase
 * algún tick el hueco se vería como una muestra "repetida" del
 * último valor conocido — aceptable.
 */
@Composable
private fun Sparkline(
    values: List<Float>,
    color: Color,
    modifier: Modifier = Modifier
) {
    if (values.size < 2) {
        // Placeholder vacío con la misma altura, para que el layout no
        // pegue saltos cuando entra la primera muestra.
        Spacer(modifier)
        return
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        // Fondo sutil (sin esto, con valores cerca de 0 la línea se pierde
        // al tener fondo del card casi del mismo color).
        drawRect(color = track.copy(alpha = 0.35f))

        val stepX = if (values.size > 1) w / (values.size - 1).toFloat() else 0f
        // Invertimos Y: valor 0 abajo, valor 1 arriba. Pequeño margen (2px)
        // para que una línea pegada al 100% no se coma el borde superior.
        val margin = 2f
        fun yFor(v: Float): Float = margin + (1f - v.coerceIn(0f, 1f)) * (h - margin * 2)

        val linePath = Path().apply {
            moveTo(0f, yFor(values[0]))
            for (i in 1 until values.size) {
                lineTo(i * stepX, yFor(values[i]))
            }
        }
        // Relleno: cerramos el path hacia abajo a la derecha y a la izquierda
        // para que quede un área semi-transparente degradada bajo la línea.
        val fillPath = Path().apply {
            addPath(linePath)
            lineTo((values.size - 1) * stepX, h)
            lineTo(0f, h)
            close()
        }
        drawPath(
            path = fillPath,
            brush = Brush.verticalGradient(
                colors = listOf(color.copy(alpha = 0.35f), color.copy(alpha = 0.0f)),
                startY = 0f,
                endY = h
            )
        )
        drawPath(
            path = linePath,
            color = color,
            style = Stroke(width = 2f, cap = StrokeCap.Round)
        )
    }
}

/** Formatea bytes/segundo con la misma escala que [humanBytes] + "/s". */
private fun humanBytesPerSec(bps: Long): String = humanBytes(bps) + "/s"

/* â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ Componentes base â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */

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

/* â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ Formatos â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€â”€ */

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
