package com.musarum.mobile.music.artists

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.data.network.dto.AlbumCardDto
import com.musarum.mobile.music.ArtistAvatar
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.music.SongActionsHost
import com.musarum.mobile.music.SongRow
import com.musarum.mobile.ui.InlineSearchBar
import com.musarum.mobile.ui.SearchActionIcon

@Composable
fun ArtistDetailScreen(
    artistId: Long,
    onBack: () -> Unit,
    onOpenAlbum: (Long) -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: ArtistDetailViewModel = viewModel(
        key = "artist_$artistId",
        factory = ArtistDetailViewModel.factory(container.artistRepository, artistId)
    )
    val state by viewModel.state.collectAsState()
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        SongActionsHost { openSongActions ->
        when (val s = state) {
            is ArtistDetailUiState.Loading -> Box(
                Modifier.fillMaxSize(), Alignment.Center
            ) { CircularProgressIndicator(color = MaterialTheme.colorScheme.primary) }

            is ArtistDetailUiState.Error -> Box(
                Modifier.fillMaxSize(), Alignment.Center
            ) { Text(s.message, color = MaterialTheme.colorScheme.error) }

            is ArtistDetailUiState.Ready -> {
                // Con lupa activa: filtra álbumes (por título) y canciones
                // (por título o artista) con el mismo texto.
                val q = query.trim().lowercase()
                val filteredAlbums = if (q.isBlank()) s.albums
                    else s.albums.filter { it.title.lowercase().contains(q) }
                val filteredSongs = if (q.isBlank()) s.songs
                    else s.songs.filter {
                        it.title.lowercase().contains(q) ||
                            it.artistsLabel.lowercase().contains(q)
                    }
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        ArtistHero(
                            imageUrl = s.artist.imageUrl,
                            name = s.artist.name,
                            country = s.artist.country,
                            beginYear = s.artist.beginYear,
                            songCount = s.artist.songCount,
                            albumCount = s.artist.albumCount,
                            totalDurationMs = s.artist.totalDurationMs,
                            onBack = onBack,
                            searching = searching,
                            onToggleSearch = {
                                searching = !searching
                                if (!searching) query = ""
                            }
                        )
                    }
                    item {
                        InlineSearchBar(
                            visible = searching,
                            query = query,
                            onQueryChange = { query = it },
                            placeholder = "Buscar en este artista"
                        )
                    }

                    // Biografía solo cuando no hay búsqueda — ocupa mucho
                    // y no ayuda a centrarse en los resultados.
                    if (query.isBlank()) {
                        s.artist.biography?.takeIf { it.isNotBlank() }?.let { bio ->
                            item { BiographyCard(bio) }
                        }
                    }

                    if (filteredAlbums.isNotEmpty()) {
                        item { SectionHeader("Discografía") }
                        item {
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(filteredAlbums, key = { it.id }) { album ->
                                    AlbumCard(album, onClick = { onOpenAlbum(album.id) })
                                }
                            }
                        }
                    }

                    if (filteredSongs.isNotEmpty()) {
                        item { SectionHeader("Canciones") }
                        itemsIndexed(filteredSongs) { index, song ->
                            SongRow(
                                song = song,
                                onClick = {
                                    container.playerController.playQueue(
                                        songs = filteredSongs,
                                        startIndex = index
                                    )
                                },
                                onLongPress = { openSongActions(song) }
                            )
                        }
                    }

                    item { Spacer(Modifier.height(100.dp)) }
                }
            }
        }
        }
    }
}

@Composable
private fun ArtistHero(
    imageUrl: String?,
    name: String,
    country: String?,
    beginYear: Int?,
    songCount: Long,
    albumCount: Long,
    totalDurationMs: Long,
    onBack: () -> Unit,
    searching: Boolean = false,
    onToggleSearch: () -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(360.dp)
    ) {
        if (!imageUrl.isNullOrBlank()) {
            AsyncImage(
                model = imageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(60.dp)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to Color.Black.copy(alpha = 0.3f),
                        0.6f to MaterialTheme.colorScheme.background.copy(alpha = 0.8f),
                        1.0f to MaterialTheme.colorScheme.background
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .padding(top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(42.dp)) {
                    Icon(
                        Icons.Filled.ArrowBack,
                        contentDescription = "Atrás",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
                Spacer(Modifier.weight(1f))
                SearchActionIcon(active = searching, onToggle = onToggleSearch)
            }

            Spacer(Modifier.weight(1f))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    modifier = Modifier.size(140.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 16.dp,
                    border = androidx.compose.foundation.BorderStroke(
                        2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.6f)
                    )
                ) {
                    ArtistAvatar(url = imageUrl, size = 140.dp)
                }
            }

            Spacer(Modifier.height(16.dp))

            Text(
                name,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )

            val stats = buildList {
                if (songCount > 0) add("$songCount ${if (songCount == 1L) "canción" else "canciones"}")
                if (albumCount > 0) add("$albumCount ${if (albumCount == 1L) "álbum" else "álbumes"}")
                if (totalDurationMs > 0) add(formatLongMinutes(totalDurationMs))
                country?.let { add(it) }
                beginYear?.let { add("desde $it") }
            }.joinToString(" · ")
            if (stats.isNotBlank()) {
                Text(
                    stats,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun BiographyCard(bio: String) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "SOBRE EL ARTISTA",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.height(6.dp))
            Text(
                bio,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 8,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleLarge,
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 10.dp, end = 16.dp)
    )
}

/**
 * Tarjeta de álbum para el carrusel horizontal: cover grande cuadrado +
 * título debajo + línea de badges (♩ nº · ⏱ min · tipo). ~160dp de ancho,
 * para que entren ~2.2 en pantalla e invite al scroll.
 */
@Composable
private fun AlbumCard(album: AlbumCardDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(160.dp)
            .clickable(onClick = onClick)
            .padding(bottom = 4.dp)
    ) {
        Surface(
            modifier = Modifier.size(160.dp),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            shadowElevation = 8.dp
        ) {
            CoverImage(url = album.coverUrl, size = 160.dp, cornerRadius = 12.dp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            album.title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        val badges = buildList {
            album.releaseYear?.let { add(it.toString()) }
            album.inferredType?.let { add(it) }
        }.joinToString(" · ")
        if (badges.isNotBlank()) {
            Text(
                badges,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
        Row(
            modifier = Modifier.padding(top = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (album.songCount > 0) {
                MiniStat(icon = Icons.Filled.MusicNote, value = album.songCount.toString())
            }
            if (album.totalDurationMs > 0) {
                MiniStat(icon = Icons.Filled.AccessTime, value = formatShortMinutes(album.totalDurationMs))
            }
        }
    }
}

@Composable
private fun MiniStat(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(13.dp)
        )
        Spacer(Modifier.width(3.dp))
        Text(
            value,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** "48 min" / "2 h 15 min" — compacto para la tarjeta del álbum. */
private fun formatShortMinutes(ms: Long): String {
    val totalMin = (ms / 60_000).toInt()
    if (totalMin < 60) return "$totalMin min"
    val h = totalMin / 60
    val m = totalMin % 60
    return if (m == 0) "${h} h" else "${h} h ${m} min"
}

/** "2 horas y 15 minutos" — para el hero del artista. */
private fun formatLongMinutes(ms: Long): String {
    val totalMin = (ms / 60_000).toInt()
    if (totalMin < 60) return "$totalMin min"
    val h = totalMin / 60
    val m = totalMin % 60
    return if (m == 0) "$h h" else "$h h $m min"
}
