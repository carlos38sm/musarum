package com.musarum.mobile.music.search

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.data.network.dto.AlbumCardDto
import com.musarum.mobile.data.network.dto.ArtistSummaryDto
import com.musarum.mobile.music.ArtistAvatar
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.music.SongRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (Long) -> Unit,
    viewModel: SearchViewModel = viewModel(
        factory = SearchViewModel.factory(
            LocalAppContainer.current.searchRepository,
            LocalAppContainer.current.playerPreferences
        )
    )
) {
    val query by viewModel.query.collectAsState()
    val state by viewModel.state.collectAsState()
    val filter by viewModel.filter.collectAsState()
    val recent by viewModel.recentSearches.collectAsState()
    val container = LocalAppContainer.current

    Scaffold(
        topBar = { TopAppBar(title = { Text("Buscar") }) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // Pastilla de búsqueda — redondeada estilo iOS, sin outline y con
            // placeholder corto. Altura compacta (52dp) con padding horizontal
            // generoso para que la lupa y el campo respiren.
            TextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = {
                    Text(
                        "Buscar en la biblioteca",
                        style = MaterialTheme.typography.bodyMedium
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(
                                Icons.Filled.Clear,
                                contentDescription = "Limpiar",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                // Teclado tipo "Buscar": pulsar la lupa dispara commitSearch,
                // que es el único punto donde registramos la query como
                // reciente. Así no se guardan intermedios ("Ba", "Bad"...).
                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                    imeAction = androidx.compose.ui.text.input.ImeAction.Search
                ),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onSearch = { viewModel.commitSearch() }
                ),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .height(52.dp)
            )

            // Chips de filtro — solo se muestran una vez hay query en marcha
            if (query.isNotBlank()) {
                FilterChipRow(current = filter, onSelect = viewModel::setFilter)
            }

            Box(modifier = Modifier.fillMaxSize()) {
                when (val s = state) {
                    is SearchUiState.Empty -> RecentSearchesPanel(
                        recent = recent,
                        onPick = viewModel::onQueryChange,
                        onClearAll = viewModel::clearRecent
                    )

                    is SearchUiState.Loading -> Box(
                        Modifier.fillMaxSize(), Alignment.Center
                    ) { CircularProgressIndicator() }

                    is SearchUiState.Error -> Box(
                        Modifier.fillMaxSize(), Alignment.Center
                    ) { Text(s.message, color = MaterialTheme.colorScheme.error) }

                    is SearchUiState.Ready -> {
                        val r = s.result
                        val showArtists = filter == SearchFilter.ALL || filter == SearchFilter.ARTISTS
                        val showAlbums  = filter == SearchFilter.ALL || filter == SearchFilter.ALBUMS
                        val showSongs   = filter == SearchFilter.ALL || filter == SearchFilter.SONGS
                        val anyVisible =
                            (showArtists && r.artists.isNotEmpty()) ||
                            (showAlbums  && r.albums.isNotEmpty()) ||
                            (showSongs   && r.songs.isNotEmpty())
                        if (!anyVisible) {
                            Box(Modifier.fillMaxSize(), Alignment.Center) {
                                Text("Sin resultados")
                            }
                        } else {
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                if (showArtists && r.artists.isNotEmpty()) {
                                    item { SectionHeader("Artistas") }
                                    items(r.artists.size, key = { "art_${r.artists[it].id}" }) { idx ->
                                        val a = r.artists[idx]
                                        ArtistResultRow(a, onClick = {
                                            viewModel.onResultTapped()
                                            onOpenArtist(a.id)
                                        })
                                        HorizontalDivider()
                                    }
                                }
                                if (showAlbums && r.albums.isNotEmpty()) {
                                    item { SectionHeader("Álbumes") }
                                    items(r.albums.size, key = { "alb_${r.albums[it].id}" }) { idx ->
                                        val a = r.albums[idx]
                                        AlbumResultRow(a, onClick = {
                                            viewModel.onResultTapped()
                                            onOpenAlbum(a.id)
                                        })
                                        HorizontalDivider()
                                    }
                                }
                                if (showSongs && r.songs.isNotEmpty()) {
                                    item { SectionHeader("Canciones") }
                                    items(r.songs.size, key = { "sng_${r.songs[it].id}" }) { idx ->
                                        val song = r.songs[idx]
                                        SongRow(
                                            song = song,
                                            onClick = {
                                                viewModel.onResultTapped()
                                                container.playerController.playQueue(
                                                    songs = r.songs,
                                                    startIndex = idx
                                                )
                                            }
                                        )
                                        HorizontalDivider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun FilterChipRow(current: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    // 2 columnas / 2 filas con FlowRow(maxItemsInEachRow=2). Antes era una
    // única fila y "Canciones" se partía en varias líneas por falta de ancho.
    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2
    ) {
        FilterChip(
            selected = current == SearchFilter.ALL,
            onClick = { onSelect(SearchFilter.ALL) },
            label = { Text("Todo") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = current == SearchFilter.ARTISTS,
            onClick = { onSelect(SearchFilter.ARTISTS) },
            label = { Text("Artistas") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = current == SearchFilter.ALBUMS,
            onClick = { onSelect(SearchFilter.ALBUMS) },
            label = { Text("Álbumes") },
            modifier = Modifier.weight(1f)
        )
        FilterChip(
            selected = current == SearchFilter.SONGS,
            onClick = { onSelect(SearchFilter.SONGS) },
            label = { Text("Canciones") },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun RecentSearchesPanel(
    recent: List<String>,
    onPick: (String) -> Unit,
    onClearAll: () -> Unit
) {
    if (recent.isEmpty()) {
        Box(Modifier.fillMaxSize(), Alignment.Center) {
            Text(
                "Empieza a escribir para buscar",
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Recientes",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            TextButton(onClick = onClearAll) { Text("Borrar") }
        }
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(recent.size, key = { "recent_$it" }) { idx ->
                val term = recent[idx]
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(term) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(14.dp))
                    Text(
                        term,
                        style = MaterialTheme.typography.bodyLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ArtistResultRow(artist: ArtistSummaryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtistAvatar(url = artist.imageUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Text(
            artist.name,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AlbumResultRow(album: AlbumCardDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(url = album.coverUrl, size = 56.dp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                album.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            album.artistName?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

