package com.musarum.mobile.music.albums

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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.data.network.dto.AlbumCardDto
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.music.LibraryStatsHeader
import com.musarum.mobile.ui.EmptyView
import com.musarum.mobile.ui.ErrorView
import com.musarum.mobile.ui.LoadingView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumsScreen(
    onOpenAlbum: (Long) -> Unit,
    viewModel: AlbumsViewModel = viewModel(
        factory = AlbumsViewModel.factory(LocalAppContainer.current.albumRepository)
    )
) {
    val state by viewModel.state.collectAsState()
    val refreshing by viewModel.refreshing.collectAsState()

    Scaffold(
        topBar = { TopAppBar(title = { Text("Álbumes") }) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when (val s = state) {
                is AlbumsUiState.Loading -> LoadingView()
                is AlbumsUiState.Error -> ErrorView(
                    message = s.message,
                    onRetry = { viewModel.refresh() }
                )
                is AlbumsUiState.Ready -> if (s.totalCount == 0) {
                    EmptyView("Sin álbumes")
                } else {
                    val gridState = rememberLazyGridState()
                    // Dispara loadMore cuando quedan <10 items por verse.
                    val shouldLoadMore by remember(s.endReached, s.loadingMore) {
                        derivedStateOf {
                            if (s.endReached || s.loadingMore) false
                            else {
                                val last = gridState.layoutInfo.visibleItemsInfo
                                    .lastOrNull()?.index ?: 0
                                last >= s.albums.size - 10
                            }
                        }
                    }
                    LaunchedEffect(gridState) {
                        snapshotFlow { shouldLoadMore }
                            .collect { if (it) viewModel.loadMore() }
                    }
                    LazyVerticalGrid(
                        state = gridState,
                        columns = GridCells.Adaptive(minSize = 150.dp),
                        contentPadding = PaddingValues(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            LibraryStatsHeader(primary = "${s.totalCount} álbumes")
                        }
                        items(s.albums, key = { it.id }) { album ->
                            AlbumGridItem(album, onClick = { onOpenAlbum(album.id) })
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

@Composable
private fun AlbumGridItem(album: AlbumCardDto, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        CoverImage(
            url = album.coverUrl,
            size = 160.dp,
            cornerRadius = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
        )
        Text(
            album.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp)
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
