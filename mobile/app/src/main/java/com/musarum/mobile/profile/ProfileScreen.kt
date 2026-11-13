package com.musarum.mobile.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.ui.humanizeDurationMs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    onLogout: () -> Unit,
    onOpenFolders: () -> Unit = {},
    onOpenUsers: () -> Unit = {},
    onOpenServerStats: () -> Unit = {},
    onOpenEqualizer: () -> Unit = {},
    onOpenPublishUpdate: () -> Unit = {},
    viewModel: ProfileViewModel = viewModel(
        factory = ProfileViewModel.factory(LocalAppContainer.current.userRepository)
    )
) {
    val container = LocalAppContainer.current
    val state by viewModel.state.collectAsState()
    val crossfadeSeconds by container.playerPreferences.crossfadeSeconds.collectAsState()
    val snackbar = LocalSnackbar.current

    var showEditNick by remember { mutableStateOf(false) }
    var showChangePassword by remember { mutableStateOf(false) }
    var showCrossfade by remember { mutableStateOf(false) }
    var showCreateUser by remember { mutableStateOf(false) }
    val isOwner = state.user?.userRole == "OWNER"

    Scaffold(
        topBar = { TopAppBar(title = { Text("Perfil") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(24.dp))

            // Cabecera: avatar clicable que abre el picker. Si hay avatarUrl,
            // lo mostramos con CoverImage; si no, caemos a la inicial del nick.
            val context = LocalContext.current
            val avatarPicker = androidx.activity.compose.rememberLauncherForActivityResult(
                contract = androidx.activity.result.contract.ActivityResultContracts
                    .PickVisualMedia()
            ) { uri ->
                if (uri == null) return@rememberLauncherForActivityResult
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                val bytes = runCatching {
                    context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                }.getOrNull()
                if (bytes != null) {
                    viewModel.uploadAvatar(bytes, mime) { _, msg -> snackbar(msg) }
                }
            }

            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clickable {
                        avatarPicker.launch(
                            androidx.activity.result.PickVisualMediaRequest(
                                androidx.activity.result.contract.ActivityResultContracts
                                    .PickVisualMedia.ImageOnly
                            )
                        )
                    }
            ) {
                val avatarUrl = state.user?.avatarUrl
                if (avatarUrl != null) {
                    com.musarum.mobile.music.ArtistAvatar(
                        url = avatarUrl,
                        size = 96.dp,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            val initial = state.user?.nick?.firstOrNull()?.uppercase()
                            if (initial != null) {
                                Text(
                                    initial,
                                    style = MaterialTheme.typography.displayMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(56.dp)
                                )
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Text(
                state.user?.nick ?: if (state.loading) "Cargando…" else "Tu cuenta",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
            state.user?.userRole?.let { role ->
                Text(
                    role.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (state.error != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    state.error ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Resumen de reproducción ──
            state.stats?.let { stats -> StatsCard(stats) }
            if (state.stats != null) Spacer(Modifier.height(18.dp))

            // ── Acciones ──
            SettingRow(
                icon = Icons.Filled.Edit,
                title = "Cambiar nick",
                subtitle = state.user?.nick ?: "—",
                onClick = { showEditNick = true },
                enabled = state.user != null
            )
            Spacer(Modifier.height(10.dp))
            SettingRow(
                icon = Icons.Filled.Lock,
                title = "Cambiar contraseña",
                subtitle = "Requiere la contraseña actual",
                onClick = { showChangePassword = true },
                enabled = state.user != null
            )
            Spacer(Modifier.height(10.dp))
            SettingRow(
                icon = Icons.Filled.Tune,
                title = "Crossfade",
                subtitle = if (crossfadeSeconds <= 0) "Desactivado"
                else "$crossfadeSeconds s entre canciones",
                onClick = { showCrossfade = true }
            )
            Spacer(Modifier.height(10.dp))
            val replayGainEnabled by container.playerPreferences
                .replayGainEnabled.collectAsState()
            SettingRowSwitch(
                icon = Icons.Filled.GraphicEq,
                title = "Normalizar volumen",
                subtitle = "Igualar volumen entre pistas",
                checked = replayGainEnabled,
                onCheckedChange = {
                    container.playerPreferences.setReplayGainEnabled(it)
                }
            )
            Spacer(Modifier.height(10.dp))
            // Convivencia: cada toggle controla una sección del Home de los
            // demás. shareNowPlaying → "En Musarum ahora"; shareTaste →
            // "Gustos de la casa". El backend ya los respeta; aquí solo
            // mostramos el estado actual y disparamos el PATCH.
            val me by container.userRepository.me.collectAsState()
            SettingRowSwitch(
                icon = Icons.Filled.Headphones,
                title = "Compartir qué escucho",
                subtitle = "Aparezco en \"En Musarum ahora\" de los demás",
                checked = me?.shareNowPlaying != false,
                onCheckedChange = { value -> viewModel.updatePrivacyNowPlaying(value) }
            )
            Spacer(Modifier.height(10.dp))
            SettingRowSwitch(
                icon = Icons.Filled.Visibility,
                title = "Mostrar mis gustos",
                subtitle = "Aparezco en los mini-perfiles de la casa",
                checked = me?.shareTaste != false,
                onCheckedChange = { value -> viewModel.updatePrivacyTaste(value) }
            )
            Spacer(Modifier.height(10.dp))
            val eqEnabled by container.playerPreferences.equalizerEnabled.collectAsState()
            SettingRow(
                icon = Icons.Filled.Tune,
                title = "Ecualizador",
                subtitle = if (eqEnabled) "Activado · 5 bandas" else "Desactivado",
                onClick = onOpenEqualizer
            )
            Spacer(Modifier.height(10.dp))
            // Permite al OWNER (o a cualquier usuario) abrir los ajustes de
            // batería para desactivar la optimización. Sin ella, Android/Doze
            // corta la red con la pantalla apagada y los Bluetooth se quedan
            // sin bytes → handshake → algunos modelos se apagan.
            val ctx = LocalContext.current
            SettingRow(
                icon = Icons.Filled.BatteryChargingFull,
                title = "Permitir en segundo plano",
                subtitle = "Evita cortes de Bluetooth con la pantalla bloqueada",
                onClick = {
                    runCatching {
                        val intent = android.content.Intent(
                            android.provider.Settings
                                .ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS
                        ).apply {
                            addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        ctx.startActivity(intent)
                    }.onFailure {
                        // Fallback: abrir ajustes de la app por si el intent
                        // genérico no existe en algún ROM raro.
                        runCatching {
                            val fallback = android.content.Intent(
                                android.provider.Settings
                                    .ACTION_APPLICATION_DETAILS_SETTINGS,
                                android.net.Uri.parse("package:${ctx.packageName}")
                            ).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            ctx.startActivity(fallback)
                        }
                    }
                }
            )
            Spacer(Modifier.height(10.dp))

            // ── Descargas offline ─────────────────────────────────────────
            val downloads by container.downloadsRepository.entries.collectAsState()
            val tempByDefault by container.playerPreferences
                .downloadsTemporaryByDefault.collectAsState()
            val totalMb = (downloads.values.sumOf { it.sizeBytes } / (1024.0 * 1024.0))
            SettingRowSwitch(
                icon = Icons.Filled.CloudDownload,
                title = "Descargas temporales",
                subtitle = "Caducan a las 24 h",
                checked = tempByDefault,
                onCheckedChange = {
                    container.playerPreferences.setDownloadsTemporaryByDefault(it)
                }
            )
            Spacer(Modifier.height(10.dp))
            SettingRow(
                icon = Icons.Filled.DeleteSweep,
                title = "Borrar todas las descargas",
                subtitle = if (downloads.isEmpty()) "No hay canciones descargadas"
                           else "${downloads.size} canciones · ${"%.1f".format(totalMb)} MB",
                enabled = downloads.isNotEmpty(),
                onClick = {
                    container.downloadsManager.clearAll()
                    snackbar("Descargas borradas")
                }
            )
            Spacer(Modifier.height(10.dp))
            // Subtítulo dinámico: mientras scanning=true muestra el contador
            // "1200/7000" en vivo, incluyendo el fichero actual cuando viene.
            // Al terminar, muestra el resumen del último escaneo si existe
            // (p. ej. "15 nuevas · 3 duplicadas").
            val scanSubtitle = run {
                val s = state.scannerStatus
                when {
                    state.scanning && s != null && s.total > 0 -> {
                        val cur = s.currentFile?.take(28) ?: ""
                        val label = when (s.phase) {
                            "hashing" -> "Analizando"
                            "importing" -> "Importando"
                            else -> "Escaneando"
                        }
                        if (cur.isNotEmpty()) "$label ${s.processed}/${s.total} · $cur"
                        else "$label ${s.processed}/${s.total}"
                    }
                    state.scanning -> "Preparando…"
                    s?.lastResult != null -> {
                        val r = s.lastResult
                        buildString {
                            append("${r.imported} nuevas")
                            if (r.duplicates > 0) append(" · ${r.duplicates} duplicadas")
                            if (r.deleted > 0) append(" · ${r.deleted} borradas")
                        }
                    }
                    else -> "Busca canciones nuevas en el servidor"
                }
            }
            val scanTitle = when {
                !state.scanning -> "Rescanear biblioteca"
                state.scannerStatus?.phase == "hashing" -> "Analizando ficheros…"
                state.scannerStatus?.phase == "importing" -> "Importando canciones…"
                else -> "Escaneando…"
            }
            SettingRow(
                icon = if (state.scanning) Icons.Filled.Refresh else Icons.Filled.LibraryMusic,
                title = scanTitle,
                subtitle = scanSubtitle,
                enabled = !state.scanning,
                onClick = {
                    viewModel.triggerScan { _, msg -> snackbar(msg) }
                }
            )

            // Alta y gestión de usuarios + carpetas: solo visible para el
            // OWNER. Un MEMBER no puede ver siquiera la opción (y el backend
            // bloquearía 403 aunque la viese).
            if (isOwner) {
                Spacer(Modifier.height(10.dp))
                SettingRow(
                    icon = Icons.Filled.PersonAdd,
                    title = "Crear usuario",
                    subtitle = "Alta de nuevo oyente",
                    onClick = { showCreateUser = true }
                )
                Spacer(Modifier.height(10.dp))
                SettingRow(
                    icon = Icons.Filled.Group,
                    title = "Usuarios",
                    subtitle = "Ver, bloquear o eliminar oyentes",
                    onClick = onOpenUsers
                )
                Spacer(Modifier.height(10.dp))
                SettingRow(
                    icon = Icons.Filled.Folder,
                    title = "Carpetas de música",
                    subtitle = "Elige qué subcarpetas se escanean",
                    onClick = onOpenFolders
                )
                Spacer(Modifier.height(10.dp))
                SettingRow(
                    icon = Icons.Filled.Insights,
                    title = "Análisis del servidor",
                    subtitle = "Catálogo, letras, disco y actividad",
                    onClick = onOpenServerStats
                )
                Spacer(Modifier.height(10.dp))
                SettingRow(
                    icon = Icons.Filled.NewReleases,
                    title = "Publicar actualización",
                    subtitle = "Anunciar nueva versión del APK",
                    onClick = onOpenPublishUpdate
                )
            }

            Spacer(Modifier.height(28.dp))

            Button(
                onClick = onLogout,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                ),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Text("Cerrar sesión", fontWeight = FontWeight.SemiBold)
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showEditNick) {
        EditNickDialog(
            initial = state.user?.nick.orEmpty(),
            saving = state.savingNick,
            onDismiss = { showEditNick = false },
            onConfirm = { newNick ->
                viewModel.updateNick(newNick) { ok, message ->
                    snackbar(message)
                    if (ok) showEditNick = false
                }
            }
        )
    }

    if (showChangePassword) {
        ChangePasswordDialog(
            saving = state.changingPassword,
            onDismiss = { showChangePassword = false },
            onConfirm = { current, new ->
                viewModel.changePassword(current, new) { ok, message ->
                    snackbar(message)
                    if (ok) showChangePassword = false
                }
            }
        )
    }

    if (showCrossfade) {
        CrossfadeDialog(
            value = crossfadeSeconds,
            onChange = { container.playerPreferences.setCrossfadeSeconds(it) },
            onDismiss = { showCrossfade = false }
        )
    }

    if (showCreateUser) {
        CreateUserDialog(
            onDismiss = { showCreateUser = false },
            onConfirm = { nick, password, asOwner ->
                viewModel.createUser(nick, password, asOwner) { ok, message ->
                    snackbar(message)
                    if (ok) showCreateUser = false
                }
            }
        )
    }
}

/**
 * Diálogo de alta de usuario (solo OWNER). Mínimos de validación en UI
 * (3-30 chars nick, 8+ contraseña) para no cargar el backend con pelotas
 * fáciles; la validación real la hace el backend igual.
 */
@Composable
private fun CreateUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (nick: String, password: String, asOwner: Boolean) -> Unit
) {
    var nick by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var asOwner by remember { mutableStateOf(false) }

    val nickValid = nick.length in 3..30 && nick.matches(Regex("^[a-zA-Z0-9_-]+$"))
    val passValid = password.length >= 8
    val canCreate = nickValid && passValid

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crear usuario") },
        text = {
            Column {
                OutlinedTextField(
                    value = nick,
                    onValueChange = { nick = it.trim() },
                    label = { Text("Nick") },
                    singleLine = true,
                    supportingText = {
                        Text("3–30 caracteres · letras, números, _ y -")
                    },
                    isError = nick.isNotEmpty() && !nickValid
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    supportingText = { Text("Mínimo 8 caracteres") },
                    isError = password.isNotEmpty() && !passValid
                )
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = asOwner,
                        onCheckedChange = { asOwner = it }
                    )
                    Spacer(Modifier.width(4.dp))
                    Column {
                        Text(
                            "Marcar como OWNER",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Podrá crear otros usuarios y borrar canciones",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = canCreate,
                onClick = { onConfirm(nick, password, asOwner) }
            ) { Text("Crear") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

@Composable
private fun StatsCard(stats: com.musarum.mobile.data.network.dto.UserStatsDto) {
    // Formato "bento": header, tarjeta destacada full-width con el tiempo
    // total escuchado, debajo dos tiles 1:1 (escuchas · distintas) y al pie
    // una lista compacta con los tops (más escuchada / artista / género).
    // Más visual que la triple columna anterior y los números respiran.
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "TUS NÚMEROS",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.5.sp
            )
        }
        Spacer(Modifier.height(10.dp))

        // Tile destacado: tiempo total — el stat "wow" de la pantalla.
        HeroStatTile(
            icon = Icons.Filled.Schedule,
            value = humanizeDurationMs(stats.totalListenedMs),
            label = "escuchados en total"
        )
        Spacer(Modifier.height(10.dp))

        // Dos tiles 1:1 debajo: nº de reproducciones y pistas distintas.
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            BentoStatTile(
                icon = Icons.Filled.PlayArrow,
                value = stats.totalPlays.toString(),
                label = "escuchas",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f)
            )
            BentoStatTile(
                icon = Icons.Filled.MusicNote,
                value = stats.uniqueSongsPlayed.toString(),
                label = "pistas distintas",
                tint = MaterialTheme.colorScheme.tertiary,
                modifier = Modifier.weight(1f)
            )
        }

        val tops = buildList {
            stats.topSongTitle?.let { add(Icons.Filled.MusicNote to ("Más escuchada" to it)) }
            stats.topArtistName?.let { add(Icons.Filled.Person to ("Artista favorito" to it)) }
            stats.topGenreName?.let { add(Icons.Filled.Album to ("Género favorito" to it)) }
        }
        if (tops.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            ) {
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                    tops.forEach { (icon, data) ->
                        val (label, value) = data
                        TopRow(icon = icon, label = label, value = value)
                    }
                }
            }
        }
    }
}

/** Tile grande (full-width) con icono + número + label apilados verticalmente. */
@Composable
private fun HeroStatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(52.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
            Spacer(Modifier.width(16.dp))
            Column {
                Text(
                    value,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** Tile pequeño (1:1 en Row.weight) con icono arriba y número + label. */
@Composable
private fun BentoStatTile(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String,
    label: String,
    tint: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = tint.copy(alpha = 0.12f)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
            )
            Text(
                value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun TopRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier.size(32.dp),
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SettingRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (enabled) 0.5f else 0.3f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Variante de SettingRow con un Switch en vez de chevron. Para ajustes
 *  tipo on/off que no abren diálogo. */
@Composable
private fun SettingRowSwitch(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            androidx.compose.material3.Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

@Composable
private fun EditNickDialog(
    initial: String,
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar nick") },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                label = { Text("Nuevo nick") },
                singleLine = true,
                enabled = !saving,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(
                enabled = !saving && value.trim().length in 3..30 && value.trim() != initial,
                onClick = { onConfirm(value.trim()) }
            ) { Text(if (saving) "Guardando…" else "Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
        }
    )
}

@Composable
private fun ChangePasswordDialog(
    saving: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (current: String, new: String) -> Unit
) {
    var current by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    val mismatch = new.isNotEmpty() && confirm.isNotEmpty() && new != confirm
    val tooShort = new.isNotEmpty() && new.length < 6

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cambiar contraseña") },
        text = {
            Column {
                OutlinedTextField(
                    value = current,
                    onValueChange = { current = it },
                    label = { Text("Contraseña actual") },
                    singleLine = true,
                    enabled = !saving,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = new,
                    onValueChange = { new = it },
                    label = { Text("Nueva contraseña (mín. 6)") },
                    singleLine = true,
                    enabled = !saving,
                    isError = tooShort,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = confirm,
                    onValueChange = { confirm = it },
                    label = { Text("Confirmar nueva") },
                    singleLine = true,
                    enabled = !saving,
                    isError = mismatch,
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                if (mismatch) {
                    Text(
                        "Las contraseñas no coinciden",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = !saving && current.isNotBlank() && new.length >= 6 && new == confirm,
                onClick = { onConfirm(current, new) }
            ) { Text(if (saving) "Guardando…" else "Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Cancelar") }
        }
    )
}

@Composable
private fun CrossfadeDialog(
    value: Int,
    onChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Crossfade") },
        text = {
            Column {
                Text(
                    text = if (value <= 0) "Desactivado — corte seco entre canciones."
                    else "$value segundos de fundido entre canciones.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Slider(
                    value = value.toFloat(),
                    onValueChange = { onChange(it.toInt()) },
                    valueRange = 0f..12f,
                    steps = 11
                )
                Row(modifier = Modifier.fillMaxWidth()) {
                    Text("0", style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                    Text(
                        "12 s",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Listo") }
        }
    )
}
