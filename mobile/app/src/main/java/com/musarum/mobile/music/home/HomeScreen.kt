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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.MicExternalOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
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
import com.musarum.mobile.data.network.dto.GenreDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.music.getGenreVisual

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenSongs: () -> Unit,
    onOpenAlbums: () -> Unit,
    onOpenArtists: () -> Unit,
    onOpenGenre: (Long) -> Unit,
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

/** Título de sección + chevron opcional alineado a la derecha (vacío por ahora
 *  — reservado para cuando los apartados tengan "ver todo"). */
@Composable
private fun SectionBar(
    title: String,
    onSeeAll: (() -> Unit)? = null
) {
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
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f)
        )
        if (onSeeAll != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.clickable(onClick = onSeeAll)
            ) {
                Text(
                    "Ver todo",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
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
