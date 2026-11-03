package com.musarum.mobile.music.artists

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.data.network.dto.ArtistSummaryDto
import com.musarum.mobile.music.ArtistAvatar
import com.musarum.mobile.music.LibraryStatsHeader
import com.musarum.mobile.ui.EmptyView
import com.musarum.mobile.ui.ErrorView
import com.musarum.mobile.ui.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistsScreen(
    onOpenArtist: (Long) -> Unit,
    viewModel: ArtistsViewModel = viewModel(
        factory = ArtistsViewModel.factory(LocalAppContainer.current.artistRepository)
    )
) {
    val state by viewModel.state.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Artistas") }) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when (val s = state) {
                is ArtistsUiState.Loading -> LoadingView()
                is ArtistsUiState.Error -> ErrorView(
                    message = s.message,
                    onRetry = { viewModel.refresh() }
                )
                is ArtistsUiState.Ready -> if (s.totalCount == 0) {
                    EmptyView("Sin artistas")
                } else {
                    val gridState = rememberLazyGridState()
                    val shouldLoadMore by remember(s.endReached, s.loadingMore) {
                        derivedStateOf {
                            if (s.endReached || s.loadingMore) false
                            else {
                                val last = gridState.layoutInfo.visibleItemsInfo
                                    .lastOrNull()?.index ?: 0
                                last >= s.artists.size - 10
                            }
                        }
                    }
                    LaunchedEffect(gridState) {
                        snapshotFlow { shouldLoadMore }
                            .collect { if (it) viewModel.loadMore() }
                    }
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 120.dp),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibraryStatsHeader(primary = "${s.totalCount} artistas")
                        }
                        items(s.artists, key = { it.id }) { artist ->
                            ArtistBubble(artist = artist, onClick = { onOpenArtist(artist.id) })
                        }
                        if (s.loadingMore) {
                            item(span = { GridItemSpan(maxLineSpan) }) {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) { CircularProgressIndicator() }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Celda tipo Spotify: avatar circular grande + nombre centrado debajo.
 */
@Composable
private fun ArtistBubble(artist: ArtistSummaryDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        ArtistAvatar(
            url = artist.imageUrl,
            size = 110.dp,
            modifier = Modifier.aspectRatio(1f)
        )
        Text(
            artist.name,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(top = 8.dp, start = 4.dp, end = 4.dp)
        )
    }
}
