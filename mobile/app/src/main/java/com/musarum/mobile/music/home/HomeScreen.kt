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
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.MicExternalOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SelfImprovement
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import com.musarum.mobile.data.network.dto.DiscoveryPageDto
import com.musarum.mobile.data.network.dto.GenreDto
import com.musarum.mobile.data.network.dto.NowPlayingUserDto
import com.musarum.mobile.data.network.dto.SmartPlaylistInfoDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.data.network.dto.TasteProfileDto
import com.musarum.mobile.music.ArtistAvatar
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.music.getGenreVisual

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

    androidx.compose.runtime.LaunchedEffect(Unit) {
        if (me == null) {
            runCatching { container.userRepository.refresh() }
        }
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
                verticalArrangement = Arrangement.spacedBy(22.dp)
            ) {
                item { HeroHeader(nick = me?.nick, avatarUrl = me?.avatarUrl) }

                state.lastPlayed?.let { last ->
                    item {
                        ContinueHero(
                            song = last,
                            onPlay = { container.playerController.playQueue(listOf(last), 0) }
                        )
                    }
                }

                item {
                    QuickChips(
                        onOpenSongs = onOpenSongs,
                        onOpenAlbums = onOpenAlbums,
                        onOpenArtists = onOpenArtists
                    )
                }

                if (state.topWeek.isNotEmpty()) {
                    item {
                        SectionBar(
                            title = "Top de la semana",
                            onSeeAll = null
                        )
                    }
                    item {
                        TopPodium(
                            songs = state.topWeek.take(3),
                            onPlay = { idx ->
                                container.playerController.playQueue(state.topWeek, idx)
                            }
                        )
                    }
                }

                if (state.recent.isNotEmpty()) {
                    item { SectionBar(title = "Vuelve a escuchar") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            itemsIndexed(state.recent) { idx, song ->
                                RecentCard(
                                    song = song,
                                    onClick = {
                                        container.playerController.playQueue(state.recent, idx)
                                    }
                                )
                            }
                        }
                    }
                }

                // ── En Musarum ahora: presencia en vivo de los otros 3 users ──
                if (state.presence.isNotEmpty()) {
                    item { SectionBar(title = "En Musarum ahora") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.presence, key = { it.userId }) { entry ->
                                PresenceCard(
                                    entry = entry,
                                    onPlay = {
                                        container.playerController.playQueue(listOf(entry.song), 0)
                                    }
                                )
                            }
                        }
                    }
                }

                // ── Nuevos descubrimientos: carrusel + "Ver todas" en header ──
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

                // ── Hecho para ti: listas inteligentes generadas al vuelo ──
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

                // ── Gustos de la casa: mini-perfiles de otros users ──
                if (state.tasteProfiles.isNotEmpty()) {
                    item { SectionBar(title = "Gustos de la casa") }
                    items(state.tasteProfiles, key = { it.userId }) { profile ->
                        TasteProfileCard(
                            profile = profile,
                            onOpenArtist = onOpenArtist,
                            onOpenGenre = onOpenGenre,
                            onPlaySong = { song ->
                                container.playerController.playQueue(listOf(song), 0)
                            }
                        )
                    }
                }

                if (state.genres.isNotEmpty()) {
                    item { SectionBar(title = "Explora por género") }
                    item {
                        LazyRow(
                            contentPadding = PaddingValues(horizontal = 20.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            items(state.genres, key = { it.id }) { genre ->
                                GenreCard(genre = genre, onClick = { onOpenGenre(genre.id) })
                            }
                        }
                    }
                }

                if (state.lastPlayed == null && state.topWeek.isEmpty() && state.recent.isEmpty()) {
                    item {
                        Text(
                            "Reproduce algo para que esto se llene — todo lo que escuches más de 30s pasará por aquí.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 24.dp, vertical = 24.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Cabecera "hero" de Inicio. Un gradiente muy sutil desde primary hacia
 * transparente da calidez a la parte alta sin saturar. El avatar es grande
 * (76dp) con borde dorado como guiño al logo. Dos líneas: saludo corto
 * en labelLarge + nick en displaySmall para que destaque sin top app bar.
 */
@Composable
private fun HeroHeader(nick: String?, avatarUrl: String?) {
    val greeting = remember { pickGreeting() }
    val scheme = MaterialTheme.colorScheme

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to scheme.primary.copy(alpha = 0.10f),
                    1f to Color.Transparent
                )
            )
            .padding(horizontal = 24.dp, vertical = 20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .background(scheme.primary.copy(alpha = 0.22f), CircleShape)
                    .padding(3.dp)
            ) {
                if (avatarUrl != null) {
                    com.musarum.mobile.music.ArtistAvatar(url = avatarUrl, size = 70.dp)
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
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.primary
                                )
                            } else {
                                Icon(
                                    Icons.Filled.Person,
                                    contentDescription = null,
                                    tint = scheme.primary,
                                    modifier = Modifier.size(40.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.width(18.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    greeting,
                    style = MaterialTheme.typography.labelLarge,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    nick ?: "Hola",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = 30.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    color = scheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

/**
 * Hero de "Continúa escuchando". Card grande con el cover ocupando el lado
 * izquierdo (120dp) y a la derecha el chip accent + título + artista + un
 * play prominente. Fondo con gradient diagonal que sugiere "este es el
 * protagonista de Inicio".
 */
@Composable
private fun ContinueHero(song: SongSummaryDto, onPlay: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .clickable(onClick = onPlay),
        shape = RoundedCornerShape(24.dp),
        color = Color.Transparent,
        tonalElevation = 0.dp
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
                CoverImage(url = song.coverUrl, size = 112.dp, cornerRadius = 18.dp)
                Spacer(Modifier.width(16.dp))
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
                    Spacer(Modifier.height(10.dp))
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = scheme.onBackground,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        song.artistsLabel,
                        style = MaterialTheme.typography.bodyMedium,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.width(8.dp))
                Surface(
                    shape = CircleShape,
                    color = scheme.primary,
                    modifier = Modifier.size(52.dp).clickable(onClick = onPlay)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = "Reproducir",
                            tint = scheme.onPrimary,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

/** Fila de 3 chips con icono + etiqueta. Antes eran 4 (incluía Favoritas)
 *  pero el texto quedaba muy chico y la fila cargada. Favoritas tiene su
 *  tile dedicado en Biblioteca y long-press en cualquier fila lo cubre. */
@Composable
private fun QuickChips(
    onOpenSongs: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        QuickChip(
            modifier = Modifier.weight(1f),
            label = "Canciones",
            icon = Icons.Filled.MusicNote,
            accent = scheme.primary,
            onClick = onOpenSongs
        )
        QuickChip(
            modifier = Modifier.weight(1f),
            label = "Álbumes",
            icon = Icons.Filled.Album,
            accent = scheme.tertiary,
            onClick = onOpenAlbums
        )
        QuickChip(
            modifier = Modifier.weight(1f),
            label = "Artistas",
            icon = Icons.Filled.MicExternalOn,
            accent = scheme.secondary,
            onClick = onOpenArtists
        )
    }
}

@Composable
private fun QuickChip(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    accent: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .height(76.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = accent.copy(alpha = 0.14f)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            Spacer(Modifier.height(4.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

/** Título de sección + chevron opcional alineado a la derecha + badge
 *  numérico (para "Nuevos descubrimientos — 12"). El badge va pegado al
 *  título en forma de pill primary tenue. */
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

/**
 * Tarjeta "En Musarum ahora": avatar del user + cover pequeño del tema, nick,
 * título de canción y "hace Xm". Toque = reproduce la canción. Pensada para
 * un carrusel horizontal de 3 tarjetas máximo (el OWNER + 3 members).
 */
@Composable
private fun PresenceCard(entry: NowPlayingUserDto, onPlay: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .width(240.dp)
            .clickable(onClick = onPlay),
        shape = RoundedCornerShape(18.dp),
        color = scheme.secondary.copy(alpha = 0.12f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(32.dp)) {
                    if (entry.avatarUrl != null) {
                        ArtistAvatar(url = entry.avatarUrl, size = 32.dp)
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = scheme.secondary.copy(alpha = 0.3f),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                val initial = entry.nick.firstOrNull()?.uppercase()
                                Text(
                                    initial ?: "·",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.secondary
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.nick,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        formatAgo(entry.playedSecondsAgo),
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
                Icon(
                    Icons.Filled.Headphones,
                    contentDescription = null,
                    tint = scheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CoverImage(url = entry.song.coverUrl, size = 48.dp, cornerRadius = 8.dp)
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        entry.song.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        entry.song.artistsLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

/**
 * Card de "Nuevos descubrimientos". Mismo formato que RecentCard pero con
 * un badge "NUEVO" encima de la portada — hace que visualmente destaque
 * frente al carrusel de "Vuelve a escuchar".
 */
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

/**
 * Tarjeta de mini-perfil semanal. Encima el avatar + nick + plays, debajo
 * tres mini-chips: "A X le gusta <artista>", "<género>" y "<canción>".
 * Cada chip con icono distinto y tap propio. Si algún top-* es null, el
 * chip correspondiente desaparece.
 */
@Composable
private fun TasteProfileCard(
    profile: TasteProfileDto,
    onOpenArtist: (Long) -> Unit,
    onOpenGenre: (Long) -> Unit,
    onPlaySong: (SongSummaryDto) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceVariant.copy(alpha = 0.55f)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(40.dp)) {
                    if (profile.avatarUrl != null) {
                        ArtistAvatar(url = profile.avatarUrl, size = 40.dp)
                    } else {
                        Surface(
                            shape = CircleShape,
                            color = scheme.primary.copy(alpha = 0.22f),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    profile.nick.firstOrNull()?.uppercase() ?: "·",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = scheme.primary
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        profile.nick,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = scheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        "${profile.playsThisWeek} escuchas esta semana",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(10.dp))
            profile.topArtist?.let { top ->
                TasteRow(
                    icon = Icons.Filled.Whatshot,
                    accent = scheme.primary,
                    text = "A ${profile.nick} le gusta ${top.name}",
                    onClick = { onOpenArtist(top.id) }
                )
            }
            profile.topGenre?.let { top ->
                Spacer(Modifier.height(6.dp))
                TasteRow(
                    icon = Icons.Filled.AutoAwesome,
                    accent = scheme.tertiary,
                    text = "Escucha mucho ${top.name}",
                    onClick = { onOpenGenre(top.id) }
                )
            }
            profile.topSong?.let { top ->
                Spacer(Modifier.height(6.dp))
                TasteRow(
                    icon = Icons.Filled.MusicNote,
                    accent = scheme.secondary,
                    text = "${top.title} · ${top.artistsLabel}",
                    onClick = {
                        onPlaySong(
                            SongSummaryDto(
                                id = top.id,
                                title = top.title,
                                durationMs = null,
                                trackNumber = null,
                                artists = emptyList(),
                                coverUrl = top.coverUrl
                            )
                        )
                    }
                )
            }
        }
    }
}

@Composable
private fun TasteRow(
    icon: ImageVector,
    accent: Color,
    text: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = accent.copy(alpha = 0.10f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                text,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun formatAgo(seconds: Long): String {
    if (seconds < 60) return "ahora mismo"
    val minutes = seconds / 60
    if (minutes < 60) return "hace ${minutes}m"
    val hours = minutes / 60
    return "hace ${hours}h"
}

/**
 * Tarjeta de lista inteligente. Fondo con gradient temático + icono grande
 * tras un halo + título bold + subtítulo. Mientras {@code loading=true} se
 * superpone un spinner para indicar que el fetch está en marcha. Clickable
 * en toda la superficie — un solo tap = reproducir.
 */
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
            // Icono grande semitransparente tipo "marca de agua" a la derecha.
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
                // Botón play circular como affordance clara. No interactivo
                // por separado: toda la tarjeta ya es clickable.
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

/**
 * Mapeo semántico iconHint → icono + paleta. Lo fija el móvil, no el
 * backend — así podemos cambiar la estética sin tocar API.
 */
@Composable
private fun smartVisualFor(iconHint: String, scheme: androidx.compose.material3.ColorScheme): SmartVisual {
    return when (iconHint) {
        "party" -> SmartVisual(
            icon = Icons.Filled.Celebration,
            top = Color(0xFFE91E63),     // rosa fuerte
            bottom = Color(0xFF9C27B0)   // morado
        )
        "focus" -> SmartVisual(
            icon = Icons.Filled.SelfImprovement,
            top = Color(0xFF26A69A),     // teal
            bottom = Color(0xFF1565C0)   // azul profundo
        )
        "undiscovered" -> SmartVisual(
            icon = Icons.Filled.Explore,
            top = Color(0xFFFFB300),     // ámbar
            bottom = Color(0xFFEF6C00)   // naranja
        )
        "forgotten" -> SmartVisual(
            icon = Icons.Filled.History,
            top = Color(0xFF5E35B1),     // púrpura calmo
            bottom = Color(0xFF283593)   // añil
        )
        else -> SmartVisual(
            icon = Icons.Filled.AutoAwesome,
            top = scheme.primary,
            bottom = scheme.tertiary
        )
    }
}

/**
 * Top 3 de la semana en formato "podio": una lista vertical (no carrusel)
 * donde cada fila lleva un indicador grande con el rank (medalla). La fila
 * 1 destaca con fondo primary suave, la 2 y 3 con surface.
 */
@Composable
private fun TopPodium(songs: List<SongSummaryDto>, onPlay: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        songs.forEachIndexed { idx, song ->
            val rank = idx + 1
            val isFirst = rank == 1
            Surface(
                modifier = Modifier.fillMaxWidth().clickable { onPlay(idx) },
                shape = RoundedCornerShape(16.dp),
                color = if (isFirst) scheme.primary.copy(alpha = 0.14f)
                        else scheme.surfaceVariant.copy(alpha = 0.6f)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = when (rank) {
                                    1 -> scheme.primary
                                    2 -> scheme.tertiary
                                    else -> scheme.secondary
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$rank",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Black,
                            color = when (rank) {
                                1 -> scheme.onPrimary
                                2 -> scheme.onTertiary
                                else -> scheme.onSecondary
                            }
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    CoverImage(url = song.coverUrl, size = 54.dp, cornerRadius = 10.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            song.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = scheme.onBackground,
                            maxLines = 1,
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
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = "Reproducir",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun RecentCard(song: SongSummaryDto, onClick: () -> Unit) {
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

/**
 * Tile de género más ambicioso: 150×100, gradient + icono grande semi-
 * transparente al fondo (como "marca de agua") y el nombre en la esquina
 * inferior en bold grande.
 */
@Composable
private fun GenreCard(genre: GenreDto, onClick: () -> Unit) {
    val visual = getGenreVisual(genre.name)
    Surface(
        modifier = Modifier
            .size(width = 150.dp, height = 100.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = Color.Transparent
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.linearGradient(
                        0f to visual.colorTop,
                        1f to visual.colorBottom
                    )
                )
        ) {
            Icon(
                visual.icon,
                contentDescription = null,
                tint = Color.White.copy(alpha = 0.18f),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 8.dp, end = 8.dp)
                    .size(56.dp)
            )
            Text(
                genre.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 10.dp, bottom = 10.dp)
            )
        }
    }
}

private fun pickGreeting(): String {
    val hour = java.time.LocalTime.now().hour
    val timeGreeting = when (hour) {
        in 6..11  -> "Buenos días"
        in 12..19 -> "Buenas tardes"
        else      -> "Buenas noches"
    }
    val variants = listOf(
        timeGreeting,
        "Bienvenido de nuevo",
        "Qué suene algo bueno",
        timeGreeting,
        "Hola otra vez",
        timeGreeting
    )
    return variants.random()
}
