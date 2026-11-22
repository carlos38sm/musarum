package com.musarum.mobile.music.home

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Celebration
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.MicExternalOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.data.network.dto.SmartPlaylistInfoDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.music.CoverImage
import kotlinx.coroutines.launch

// ─────────────────────────────────────────────────────────────────────
// Home v2 — rediseño estilo Apple Music (nov-2026)
//
// El Home antiguo apilaba 9 secciones (greeting ocupado, 3 QuickTiles,
// presence, descubrimientos, taste profile, smart playlists, podio
// semanal, carrusel de géneros) y Carlos reportó que solo usaba una.
// En Fase 1 reorganizamos en pocos bloques intencionales:
//
//   1. Saludo de 1 línea
//   2. Continúa escuchando (si hay lastPlayed)
//   3. SongsHero — bloque principal, atajo a SongsScreen + shuffle all
//   4. Vuelve a escuchar (carrusel compacto)
//   5. Nuevos descubrimientos (carrusel compacto)
//   6. Hecho para ti (listas inteligentes)
//   7. Accesos: Álbumes · Artistas (chips pequeños)
//
// Fuera: podio Top semana, Presencia, Perfil de gustos, Géneros carrusel
// (Presencia se recupera en Fase 2 dentro del nuevo bloque "Comunidad").
// ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSongs: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenGenre: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit = {},
    onOpenSong: (Long) -> Unit = {},
    onOpenDiscoveries: () -> Unit = {},
    onOpenFavorites: () -> Unit = {},
    onOpenInbox: () -> Unit = {},
    viewModel: HomeViewModel = viewModel(
        factory = HomeViewModel.factory(
            LocalAppContainer.current.playHistoryRepository,
            LocalAppContainer.current.apiService
        )
    )
) {
    val container = LocalAppContainer.current
    val state by viewModel.state.collectAsState()
    val me by container.userRepository.me.collectAsState()
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    val inboxUnreadState = remember { mutableStateOf(0L) }
    val inboxUnread = inboxUnreadState.value
    LaunchedEffect(Unit) {
        if (me == null) {
            runCatching { container.userRepository.refresh() }
        }
        runCatching { container.apiService.getInboxUnreadCount() }
            .onSuccess { inboxUnreadState.value = it["count"] ?: 0L }
    }

    Scaffold { padding ->
        when {
            state.loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            state.error != null -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                Text(state.error ?: "", color = MaterialTheme.colorScheme.error)
            }
            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                item {
                    CompactGreeting(
                        nick = me?.nick,
                        avatarUrl = me?.avatarUrl,
                        inboxUnread = inboxUnread,
                        onOpenInbox = onOpenInbox
                    )
                }

                state.lastPlayed?.let { last ->
                    item {
                        ContinueHero(
                            song = last,
                            onPlay = { container.playerController.playQueue(listOf(last), 0) }
                        )
                    }
                }

                item {
                    SongsHero(
                        totalSongs = state.totalSongs,
                        onOpenSongs = onOpenSongs,
                        onShuffleAll = {
                            // Shuffle de todo el catálogo: una petición a
                            // /songs/all (ya gzipada + ETag) y aleatorio
                            // en memoria. Mejor que N shuffle sobre la
                            // ventana paginada — barajea los ~5k reales.
                            scope.launch {
                                runCatching {
                                    container.songRepository.allOrdered().shuffled()
                                }.onSuccess { all ->
                                    if (all.isNotEmpty()) {
                                        container.playerController.playQueue(all, 0)
                                    } else {
                                        snackbar("No hay canciones en el catálogo")
                                    }
                                }.onFailure {
                                    snackbar(it.message ?: "No se pudo cargar el catálogo")
                                }
                            }
                        }
                    )
                }

                if (state.recent.isNotEmpty()) {
                    item { SectionBar(title = "Vuelve a escuchar") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(state.recent) { idx, song ->
                                CoverCard(
                                    song = song,
                                    onClick = {
                                        container.playerController.playQueue(state.recent, idx)
                                    }
                                )
                            }
                        }
                    }
                }

                state.discoveries?.takeIf { it.items.isNotEmpty() }?.let { page ->
                    item {
                        SectionBar(
                            title = "Nuevos descubrimientos",
                            badge = if (page.totalNew > 0) "${page.totalNew}" else null,
                            onSeeAll = onOpenDiscoveries
                        )
                    }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(page.items) { idx, song ->
                                DiscoveryCard(
                                    song = song,
                                    onClick = {
                                        container.playerController.playQueue(page.items, idx)
                                    }
                                )
                            }
                        }
                    }
                }

                if (state.smartPlaylists.isNotEmpty()) {
                    item { SectionBar(title = "Hecho para ti") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.smartPlaylists, key = { it.slug }) { info ->
                                SmartPlaylistCard(
                                    info = info,
                                    loading = state.smartLoadingSlug == info.slug,
                                    onClick = {
                                        viewModel.loadSmartPlaylist(
                                            slug = info.slug,
                                            onReady = { songs ->
                                                container.playerController.playQueue(songs, 0)
                                            },
                                            onEmpty = {
                                                snackbar(
                                                    "Nada que mostrar en \"${info.title}\" por ahora"
                                                )
                                            }
                                        )
                                    }
                                )
                            }
                        }
                    }
                }

                item {
                    AccessChips(
                        onOpenAlbums = onOpenAlbums,
                        onOpenArtists = onOpenArtists
                    )
                }

                if (state.lastPlayed == null && state.recent.isEmpty() &&
                    state.discoveries?.items.isNullOrEmpty() &&
                    state.smartPlaylists.isEmpty()) {
                    item {
                        Text(
                            "Reproduce algo para que esto se llene — todo lo que escuches más de 30s pasará por aquí.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────── Saludo compacto ──────────────────────────────

// Antes HeroHeader era casi una pantalla propia: avatar 76dp, saludo
// en labelLarge + nick ExtraBold 30sp, gradient de fondo. Comía ~120dp
// verticales y Carlos ni lo usaba. Versión v2: 1 sola fila de ~48dp
// con avatar pequeño, saludo+nick en una línea, y el icono de bandeja.
@Composable
private fun CompactGreeting(
    nick: String?,
    avatarUrl: String?,
    inboxUnread: Long = 0L,
    onOpenInbox: () -> Unit = {}
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 20.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .background(scheme.primary.copy(alpha = 0.22f), CircleShape)
        ) {
            if (avatarUrl != null) {
                com.musarum.mobile.music.ArtistAvatar(url = avatarUrl, size = 36.dp)
            } else {
                Surface(
                    shape = CircleShape,
                    color = scheme.primary.copy(alpha = 0.25f),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        val initial = nick?.firstOrNull()?.uppercase()
                        if (initial != null) {
                            Text(
                                initial,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = scheme.primary
                            )
                        } else {
                            Icon(
                                Icons.Filled.Person,
                                contentDescription = null,
                                tint = scheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                greetingFor(nick),
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 17.sp),
                fontWeight = FontWeight.SemiBold,
                color = scheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Box {
            IconButton(onClick = onOpenInbox) {
                Icon(
                    imageVector = Icons.Filled.MailOutline,
                    contentDescription = "Bandeja",
                    tint = scheme.onSurfaceVariant
                )
            }
            if (inboxUnread > 0L) {
                Surface(
                    shape = CircleShape,
                    color = scheme.error,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(4.dp)
                        .size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = if (inboxUnread > 9) "9+" else inboxUnread.toString(),
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onError,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

private fun greetingFor(nick: String?): String {
    val hour = java.time.LocalTime.now().hour
    val prefix = when (hour) {
        in 6..11 -> "Buenos días"
        in 12..19 -> "Buenas tardes"
        else -> "Buenas noches"
    }
    val name = nick?.takeIf { it.isNotBlank() }
    return if (name != null) "$prefix, $name" else prefix
}

// ────────────────────────────── Continúa escuchando ──────────────────────────────

@Composable
private fun ContinueHero(song: SongSummaryDto, onPlay: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onPlay),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        0f to scheme.primary.copy(alpha = 0.22f),
                        1f to scheme.tertiary.copy(alpha = 0.12f)
                    )
                )
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(url = song.coverUrl, size = 96.dp, cornerRadius = 16.dp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = scheme.primary.copy(alpha = 0.22f)
                    ) {
                        Text(
                            "Continúa escuchando",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = scheme.primary,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artistsLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = scheme.primary,
                    modifier = Modifier.size(46.dp).clickable(onClick = onPlay)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Reproducir",
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
            }
        }
    }
}

// ────────────────────────────── Canciones (hero principal) ──────────────────────────────

// Bloque principal del Home — único uso que Carlos reportó. Tile grande
// full-width con gradient primary sutil. A la izquierda icono MusicNote
// gigante tipo "marca de agua", en el centro "Canciones" + contador,
// a la derecha dos botones: Shuffle (shuffle_all del catálogo) y
// play principal (abre SongsScreen). Toda la superficie es tappable;
// los botones solo refinan la acción.
@Composable
private fun SongsHero(
    totalSongs: Long,
    onOpenSongs: () -> Unit,
    onShuffleAll: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onOpenSongs),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .height(120.dp)
                .background(
                    Brush.linearGradient(
                        0f to scheme.primary.copy(alpha = 0.78f),
                        1f to scheme.tertiary.copy(alpha = 0.58f)
                    )
                )
        ) {
            // Marca de agua: icono MusicNote enorme detrás del texto,
            // semi-transparente. Estilo Apple Music / YT Music.
            Icon(
                Icons.Filled.MusicNote,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.18f),
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 14.dp)
                    .size(96.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 24.dp, end = 14.dp, top = 18.dp, bottom = 14.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        "Canciones",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1
                    )
                    Text(
                        if (totalSongs > 0L) "$totalSongs pistas en tu catálogo"
                        else "Todo tu catálogo",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.85f),
                        maxLines = 1
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.End
                ) {
                    Surface(
                        modifier = Modifier.size(40.dp).clickable(onClick = onShuffleAll),
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.22f)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.Shuffle,
                                contentDescription = "Reproducir aleatorio",
                                tint = Color.White,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(10.dp))
                    Surface(
                        modifier = Modifier.size(46.dp).clickable(onClick = onOpenSongs),
                        shape = CircleShape,
                        color = Color.White
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Filled.PlayArrow,
                                contentDescription = "Ver todas las canciones",
                                tint = scheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

// ────────────────────────────── SectionBar + tarjetas compactas ──────────────────────────────

@Composable
private fun SectionBar(
    title: String,
    badge: String? = null,
    onSeeAll: (() -> Unit)? = null
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 24.dp, end = 20.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            fontWeight = FontWeight.Bold,
            color = scheme.onBackground
        )
        if (badge != null) {
            Spacer(Modifier.width(8.dp))
            Surface(
                shape = RoundedCornerShape(50),
                color = scheme.primary.copy(alpha = 0.18f)
            ) {
                Text(
                    badge,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = scheme.primary,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 2.dp)
                )
            }
        }
        Spacer(Modifier.weight(1f))
        if (onSeeAll != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onSeeAll)
            ) {
                Text(
                    "Ver todo",
                    style = MaterialTheme.typography.labelMedium,
                    color = scheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = scheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CoverCard(song: SongSummaryDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(124.dp)
            .clickable(onClick = onClick)
    ) {
        CoverImage(url = song.coverUrl, size = 124.dp, cornerRadius = 14.dp)
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.labelLarge,
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

@Composable
private fun DiscoveryCard(song: SongSummaryDto, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .width(124.dp)
            .clickable(onClick = onClick)
    ) {
        Box {
            CoverImage(url = song.coverUrl, size = 124.dp, cornerRadius = 14.dp)
            Surface(
                shape = RoundedCornerShape(50),
                color = scheme.tertiary,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(6.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Icon(
                        Icons.Filled.NewReleases,
                        contentDescription = null,
                        tint = scheme.onTertiary,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(Modifier.width(3.dp))
                    Text(
                        "NUEVO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onTertiary,
                        fontSize = 9.sp
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            song.title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = scheme.onBackground,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            song.artistsLabel,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SmartPlaylistCard(
    info: SmartPlaylistInfoDto,
    loading: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val visual = smartVisualFor(info.iconHint, scheme)
    Surface(
        modifier = Modifier
            .size(width = 220.dp, height = 132.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to visual.top,
                        1f to visual.bottom
                    )
                )
        ) {
            Icon(
                visual.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.22f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 10.dp, end = 10.dp)
                    .size(64.dp)
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(14.dp),
                verticalArrangement = Arrangement.Bottom
            ) {
                Text(
                    info.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    info.subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (loading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp)
                    )
                }
            } else {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.22f),
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(10.dp)
                        .size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }
        }
    }
}

private data class SmartVisual(
    val icon: ImageVector,
    val top: Color,
    val bottom: Color
)

@Composable
private fun smartVisualFor(iconHint: String, scheme: androidx.compose.material3.ColorScheme): SmartVisual {
    return when (iconHint) {
        "party" -> SmartVisual(
            icon = Icons.Filled.Celebration,
            top = Color(0xFFE91E63),
            bottom = Color(0xFF9C27B0)
        )
        "focus" -> SmartVisual(
            icon = Icons.Filled.SelfImprovement,
            top = Color(0xFF26A69A),
            bottom = Color(0xFF1565C0)
        )
        "undiscovered" -> SmartVisual(
            icon = Icons.Filled.Explore,
            top = Color(0xFFFFB300),
            bottom = Color(0xFFEF6C00)
        )
        "forgotten" -> SmartVisual(
            icon = Icons.Filled.History,
            top = Color(0xFF5E35B1),
            bottom = Color(0xFF283593)
        )
        else -> SmartVisual(
            icon = Icons.Filled.AutoAwesome,
            top = scheme.primary,
            bottom = scheme.tertiary
        )
    }
}

// ────────────────────────────── Accesos (chips al final) ──────────────────────────────

// Álbumes y Artistas quedan como accesos secundarios al pie — Carlos
// los usa desde Buscar o desde una canción, no desde Home. Dos chips
// pequeños a tono con el resto en vez de los QuickTiles coloridos.
@Composable
private fun AccessChips(
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        AccessChip(
            modifier = Modifier.weight(1f),
            label = "Álbumes",
            icon = Icons.Filled.Album,
            accent = scheme.tertiary,
            onClick = onOpenAlbums
        )
        AccessChip(
            modifier = Modifier.weight(1f),
            label = "Artistas",
            icon = Icons.Filled.MicExternalOn,
            accent = scheme.secondary,
            onClick = onOpenArtists
        )
    }
}

@Composable
private fun AccessChip(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(56.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.12f)
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}
