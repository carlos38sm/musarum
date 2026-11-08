@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.musarum.mobile.music.genres

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.music.LibraryStatsHeader
import com.musarum.mobile.music.SongActionsHost
import com.musarum.mobile.music.SongRow
import com.musarum.mobile.music.getGenreVisual
import com.musarum.mobile.ui.humanizeDurationMs

@Composable
fun GenreDetailScreen(
    genreId: Long,
    onBack: () -> Unit
) {
    val container = LocalAppContainer.current
    val viewModel: GenreDetailViewModel = viewModel(
        key = "genre_$genreId",
        factory = GenreDetailViewModel.factory(container.apiService, genreId)
    )
    val state by viewModel.state.collectAsState()

    val currentTitle = (state as? GenreDetailUiState.Ready)?.genre?.name ?: "Género"
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(currentTitle) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            SongActionsHost { openSongActions ->
            when (val s = state) {
                is GenreDetailUiState.Loading -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) { CircularProgressIndicator() }

                is GenreDetailUiState.Error -> Box(
                    Modifier.fillMaxSize(), contentAlignment = Alignment.Center
                ) { Text(s.message, color = MaterialTheme.colorScheme.error) }

                is GenreDetailUiState.Ready -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        GenreHero(
                            name = s.genre.name,
                            songCount = s.songs.size,
                            totalMs = s.songs.sumOf { (it.durationMs ?: 0).toLong() },
                            onPlayAll = {
                                if (s.songs.isNotEmpty()) {
                                    container.playerController.playQueue(s.songs, 0)
                                }
                            }
                        )
                    }
                    if (s.songs.isEmpty()) {
                        item {
                            Text(
                                "Este género todavía no tiene canciones.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(24.dp)
                            )
                        }
                    } else {
                        item {
                            LibraryStatsHeader(
                                primary = "${s.songs.size} canciones",
                                secondary = humanizeDurationMs(
                                    s.songs.sumOf { (it.durationMs ?: 0).toLong() }
                                )
                            )
                        }
                        items(s.songs, key = { it.id }) { song ->
                            SongRow(
                                song = song,
                                onClick = {
                                    container.playerController.playQueue(
                                        songs = s.songs,
                                        startIndex = s.songs.indexOf(song)
                                    )
                                },
                                onLongPress = { openSongActions(song) }
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

@Composable
private fun GenreHero(
    name: String,
    songCount: Int,
    totalMs: Long,
    onPlayAll: () -> Unit
) {
    val visual = getGenreVisual(name)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(
                Brush.verticalGradient(
                    0f to visual.colorTop,
                    1f to visual.colorBottom
                )
            )
    ) {
        Icon(
            visual.icon,
            contentDescription = null,
            tint = Color.White.copy(alpha = 0.9f),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(18.dp)
                .size(44.dp)
        )
        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                name,
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = Color.White
            )
            Text(
                "$songCount canciones · ${humanizeDurationMs(totalMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }
        if (songCount > 0) {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(18.dp),
                shape = RoundedCornerShape(50),
                color = Color.White.copy(alpha = 0.95f),
                onClick = onPlayAll
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.PlayArrow,
                        contentDescription = null,
                        tint = visual.colorBottom,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "Reproducir",
                        color = visual.colorBottom,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
