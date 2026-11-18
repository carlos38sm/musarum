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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.layout.size
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicExternalOn
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.vector.ImageVector
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.data.network.dto.AlbumCardDto
import com.musarum.mobile.data.network.dto.ArtistSummaryDto
import com.musarum.mobile.music.ArtistAvatar
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.music.SongActionsHost
import com.musarum.mobile.music.SongRow
import com.musarum.mobile.voice.VoiceCommandRouter

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
    val snackbar = LocalSnackbar.current
    val voiceScope = androidx.compose.runtime.rememberCoroutineScope()

    // Router de comandos reutilizado entre invocaciones. Lo recordamos
    // porque instanciarlo en cada voz lanzada reseteaba estado futuro.
    val voiceRouter = androidx.compose.runtime.remember {
        VoiceCommandRouter(
            controller = container.playerController,
            searchRepository = container.searchRepository,
            playlistRepository = container.playlistRepository,
            scope = voiceScope
        )
    }

    // Launcher del reconocedor de voz del SO: devuelve el texto en el result.
    // No usamos SpeechRecognizer directo para no tener que montar UI propia
    // ni manejar RECORD_AUDIO aquí — la pantalla del sistema ya se encarga.
    val voiceLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) {
            val results = result.data?.getStringArrayListExtra(
                android.speech.RecognizerIntent.EXTRA_RESULTS
            )
            val spoken = results?.firstOrNull()?.trim().orEmpty()
            if (spoken.isNotEmpty()) {
                voiceRouter.route(
                    rawText = spoken,
                    onFallbackSearch = { q ->
                        viewModel.onQueryChange(q)
                        viewModel.commitSearch()
                    },
                    report = { msg -> snackbar(msg) }
                )
            }
        }
    }

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
                    } else {
                        IconButton(onClick = {
                            // Lanza el reconocedor de voz del sistema. ACTION_
                            // RECOGNIZE_SPEECH abre su propia UI; el usuario no
                            // necesita conceder RECORD_AUDIO a Musarum. El idioma
                            // se fija en "es-ES" para que acierte con acentos
                            // estañoles; si el dispositivo tiene otro,
                            // el SO degrada al que tenga disponible.
                            val intent = android.content.Intent(
                                android.speech.RecognizerIntent.ACTION_RECOGNIZE_SPEECH
                            ).apply {
                                putExtra(
                                    android.speech.RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                                    android.speech.RecognizerIntent.LANGUAGE_MODEL_FREE_FORM
                                )
                                putExtra(
                                    android.speech.RecognizerIntent.EXTRA_LANGUAGE,
                                    "es-ES"
                                )
                                putExtra(
                                    android.speech.RecognizerIntent.EXTRA_PROMPT,
                                    "Di \"pon Rihanna\", \"pausa\", \"siguiente\", …"
                                )
                            }
                            runCatching { voiceLauncher.launch(intent) }
                                .onFailure {
                                    snackbar("Reconocimiento de voz no disponible")
                                }
                        }) {
                            Icon(
                                Icons.Filled.Mic,
                                contentDescription = "Búsqueda por voz",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
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
                SongActionsHost { openSongActions ->
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
        }
    }
}

/**
 * Chips-filtro con identidad en 2×2 (FlowRow). Cada filtro tiene su propio
 * icono y color accent — sin seleccionar queda neutro, al seleccionar se
 * colorea del accent y aparece una sombra sutil. Píldora completa.
 * El 2×2 deja los nombres a tamaño cómodo (Canciones sin partirse).
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun FilterChipRow(current: SearchFilter, onSelect: (SearchFilter) -> Unit) {
    // Colores distintos y vivos para cada filtro (ámbar, coral, cian, verde).
    // Carlos quería "apagados por defecto, al pulsar vivos y cada uno un
    // color" — los dejamos neutros cuando no están seleccionados y saltan a
    // su accent cuando el user pulsa.
    val accentTodo = Color(0xFFFFB300)       // ámbar
    val accentArtistas = Color(0xFFEC407A)   // coral/rosa
    val accentAlbumes = Color(0xFF26C6DA)    // cian
    val accentCanciones = Color(0xFF9CCC65)  // verde lima
    androidx.compose.foundation.layout.FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        maxItemsInEachRow = 2
    ) {
        SearchFilterChip(
            modifier = Modifier.weight(1f),
            label = "Todo",
            icon = Icons.Filled.Apps,
            accent = accentTodo,
            selected = current == SearchFilter.ALL,
            onClick = { onSelect(SearchFilter.ALL) }
        )
        SearchFilterChip(
            modifier = Modifier.weight(1f),
            label = "Artistas",
            icon = Icons.Filled.MicExternalOn,
            accent = accentArtistas,
            selected = current == SearchFilter.ARTISTS,
            onClick = { onSelect(SearchFilter.ARTISTS) }
        )
        SearchFilterChip(
            modifier = Modifier.weight(1f),
            label = "Álbumes",
            icon = Icons.Filled.Album,
            accent = accentAlbumes,
            selected = current == SearchFilter.ALBUMS,
            onClick = { onSelect(SearchFilter.ALBUMS) }
        )
        SearchFilterChip(
            modifier = Modifier.weight(1f),
            label = "Canciones",
            icon = Icons.Filled.MusicNote,
            accent = accentCanciones,
            selected = current == SearchFilter.SONGS,
            onClick = { onSelect(SearchFilter.SONGS) }
        )
    }
}

@Composable
private fun SearchFilterChip(
    modifier: Modifier = Modifier,
    label: String,
    icon: ImageVector,
    accent: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val bg by animateColorAsState(
        targetValue = if (selected) accent else scheme.surfaceVariant.copy(alpha = 0.55f),
        label = "chipBg"
    )
    val fg by animateColorAsState(
        // Siempre blanco sobre el accent seleccionado: los 4 colores son
        // suficientemente saturados para dar contraste con blanco. Si
        // alguna vez metemos uno pastel, habría que calcular por luminance.
        targetValue = if (selected) Color.White else scheme.onSurfaceVariant,
        label = "chipFg"
    )
    Surface(
        modifier = modifier
            .height(42.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(50),
        color = Color.Transparent,
        tonalElevation = if (selected) 2.dp else 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(bg)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(16.dp)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = fg,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun RecentSearchesPanel(
    recent: List<String>,
    onPick: (String) -> Unit,
    onClearAll: () -> Unit
) {
    val container = LocalAppContainer.current
    // Cargamos las últimas reproducciones del usuario para ofrecerlas como
    // acceso rápido cuando el search está vacío (Carlos: "Poner algo en
    // búsqueda para que no quede vacío"). Son las suyas — logout limpia el
    // historial local, y recentPlays viene filtrado por user_id en backend.
    var recentPlays by remember {
        mutableStateOf<List<com.musarum.mobile.data.network.dto.SongSummaryDto>>(emptyList())
    }
    var topPlays by remember {
        mutableStateOf<List<com.musarum.mobile.data.network.dto.SongSummaryDto>>(emptyList())
    }
    LaunchedEffect(Unit) {
        runCatching { container.playHistoryRepository.recentPlays(12) }
            .onSuccess { recentPlays = it }
        runCatching { container.playHistoryRepository.topPlays("week", 12) }
            .onSuccess { topPlays = it }
    }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        if (recent.isNotEmpty()) {
            item {
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
            }
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

        if (recentPlays.isNotEmpty()) {
            item {
                Text(
                    "Últimas reproducciones",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(
                count = recentPlays.size,
                key = { idx -> "rp_${recentPlays[idx].id}" }
            ) { idx ->
                val song = recentPlays[idx]
                SongRow(
                    song = song,
                    onClick = {
                        container.playerController.playQueue(
                            songs = recentPlays,
                            startIndex = idx
                        )
                    },
                    onLongPress = {}
                )
                HorizontalDivider()
            }
        }

        if (topPlays.isNotEmpty() && recentPlays.isEmpty() && recent.isEmpty()) {
            // Solo usamos "más escuchadas de la semana" como fallback si NI
            // siquiera hay reproducciones recientes — el user es nuevo o
            // tiene la biblioteca muy pequeña. Para no saturar cuando ya
            // había contenido.
            item {
                Text(
                    "Más escuchadas esta semana",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }
            items(
                count = topPlays.size,
                key = { idx -> "tp_${topPlays[idx].id}" }
            ) { idx ->
                val song = topPlays[idx]
                SongRow(
                    song = song,
                    onClick = {
                        container.playerController.playQueue(
                            songs = topPlays,
                            startIndex = idx
                        )
                    },
                    onLongPress = {}
                )
                HorizontalDivider()
            }
        }

        if (recent.isEmpty() && recentPlays.isEmpty() && topPlays.isEmpty()) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 60.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "Empieza a escribir para buscar",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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

