package com.musarum.mobile.music.favorites

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.musarum.mobile.data.network.dto.FavoriteDto
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.ui.EmptyView
import com.musarum.mobile.ui.ErrorView
import com.musarum.mobile.ui.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesScreen(onBack: () -> Unit) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Favoritas") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            FavoritesContent()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FavoritesContent(
    viewModel: FavoritesViewModel = viewModel(
        factory = FavoritesViewModel.factory(LocalAppContainer.current.favoriteRepository)
    )
) {
    val state by viewModel.state.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()
    val container = LocalAppContainer.current

    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { viewModel.refresh() },
        modifier = Modifier.fillMaxSize()
    ) {
        when (val s = state) {
            is FavoritesUiState.Loading -> LoadingView()
            is FavoritesUiState.Error -> ErrorView(
                message = s.message,
                onRetry = { viewModel.refresh() }
            )
            is FavoritesUiState.Ready -> if (s.favorites.isEmpty()) {
                EmptyView(
                    message = "Aún no has marcado ninguna canción",
                    icon = Icons.Filled.Favorite
                )
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp)
                        ) {
                            Button(
                                onClick = {
                                    val songs = s.favorites.map { it.toSongSummary() }
                                    container.playerController.playQueue(songs, 0)
                                }
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(Modifier.width(6.dp))
                                Text("Reproducir todas")
                            }
                        }
                        HorizontalDivider()
                    }

                    items(s.favorites, key = { it.songId }) { fav ->
                        FavoriteRow(
                            favorite = fav,
                            onClick = {
                                val songs = s.favorites.map { it.toSongSummary() }
                                container.playerController.playQueue(
                                    songs = songs,
                                    startIndex = s.favorites.indexOf(fav)
                                )
                            },
                            onToggle = { viewModel.toggle(fav.songId) }
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun FavoriteRow(
    favorite: FavoriteDto,
    onClick: () -> Unit,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(url = favorite.coverUrl, size = 48.dp, cornerRadius = 6.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                favorite.songTitle,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val subtitle = buildString {
                append(favorite.artists.joinToString(", ") { it.name })
                if (!favorite.albumTitle.isNullOrBlank()) {
                    if (isNotEmpty()) append(" • ")
                    append(favorite.albumTitle)
                }
            }
            if (subtitle.isNotBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        IconButton(onClick = onToggle) {
            Icon(
                Icons.Filled.Favorite,
                contentDescription = "Quitar de favoritas",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}
