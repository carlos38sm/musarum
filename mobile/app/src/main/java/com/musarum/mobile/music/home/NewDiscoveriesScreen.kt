package com.musarum.mobile.music.home

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.data.network.dto.DiscoveryPageDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.ui.EmptyView
import com.musarum.mobile.ui.ErrorView

/**
 * Pantalla "Nuevos descubrimientos" al pulsar "Ver todo" del Home. Lista
 * vertical con hasta 100 canciones añadidas al catálogo en los últimos 30
 * días, ordenadas por fecha de alta descendente. Toque = reproducir en
 * contexto de la lista entera.
 *
 * No montamos su propio ViewModel: una sola llamada al API a entrar; si
 * quisiéramos paginación real vendría luego, pero 100 canciones son el
 * techo razonable (el carrusel del Home ya enseña las 12 más recientes).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDiscoveriesScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    var page by remember { mutableStateOf<DiscoveryPageDto?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        try {
            page = container.apiService.getHomeDiscoveries(windowDays = 30, size = 100)
            error = null
        } catch (t: Throwable) {
            error = t.message ?: "No se pudieron cargar los descubrimientos"
        } finally {
            loading = false
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.NewReleases,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.height(18.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Nuevos descubrimientos",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        }
    ) { padding ->
        when {
            loading -> Box(Modifier.fillMaxSize().padding(padding), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
            error != null -> ErrorView(
                message = error!!,
                onRetry = null,
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            page == null || page!!.items.isEmpty() -> EmptyView(
                message = "No hay canciones añadidas en los últimos 30 días",
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            else -> {
                val content = page!!
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    item {
                        Text(
                            "${content.items.size} canciones nuevas · ventana de ${content.windowDays} días",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
                        )
                    }
                    itemsIndexed(content.items, key = { _, s -> s.id }) { idx, song ->
                        DiscoveryRow(
                            song = song,
                            onClick = {
                                container.playerController.playQueue(content.items, idx)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoveryRow(song: SongSummaryDto, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CoverImage(url = song.coverUrl, size = 54.dp, cornerRadius = 10.dp)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                song.artistsLabel,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
