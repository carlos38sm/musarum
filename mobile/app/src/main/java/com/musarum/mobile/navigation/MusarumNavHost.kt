package com.musarum.mobile.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.navigation.NavBackStackEntry
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.auth.LoginScreen
import com.musarum.mobile.music.SongsScreen
import com.musarum.mobile.music.home.HomeScreen
import com.musarum.mobile.music.albums.AlbumDetailScreen
import com.musarum.mobile.music.albums.AlbumsScreen
import com.musarum.mobile.music.artists.ArtistDetailScreen
import com.musarum.mobile.music.artists.ArtistsScreen
import com.musarum.mobile.music.favorites.FavoritesScreen
import com.musarum.mobile.music.library.DownloadsScreen
import com.musarum.mobile.music.genres.GenreDetailScreen
import com.musarum.mobile.music.library.LibraryScreen
import com.musarum.mobile.music.library.PlaylistDetailScreen
import com.musarum.mobile.music.search.SearchScreen
import com.musarum.mobile.player.InfoScreen
import com.musarum.mobile.player.LyricsScreen
import com.musarum.mobile.player.MiniPlayer
import com.musarum.mobile.player.NowPlayingScreen
import com.musarum.mobile.player.VideoScreen
import com.musarum.mobile.profile.ProfileScreen
import com.musarum.mobile.profile.equalizer.EqualizerScreen
import com.musarum.mobile.profile.folders.LibraryFoldersScreen
import com.musarum.mobile.profile.server.ServerStatsScreen
import com.musarum.mobile.profile.users.UsersScreen
import com.musarum.mobile.session.SessionViewModel

@Composable
fun MusarumNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val container = LocalAppContainer.current
    val sessionViewModel: SessionViewModel = viewModel(
        factory = SessionViewModel.factory(container.authRepository)
    )
    val isAuthenticated by sessionViewModel.isAuthenticated.collectAsState()

    val startDestination = if (isAuthenticated) Routes.HOME else Routes.LOGIN

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val isLogin = currentRoute == Routes.LOGIN || currentRoute == null
    val isNowPlaying = currentRoute == Routes.NOW_PLAYING
    val isVideo = currentRoute == Routes.VIDEO
    val isPlayerSibling = currentRoute == Routes.LYRICS || currentRoute == Routes.INFO
    // Letra e Info sí muestran mini-player (son pantallas de contenido)
    // Vídeo no — ocupa toda la pantalla y gestiona su propio control.
    val showMini = !isLogin && !isNowPlaying && !isVideo
    val showBottomNav = !isLogin && !isNowPlaying && !isPlayerSibling && !isVideo

    val snackbarHostState = remember { SnackbarHostState() }
    val snackbarScope = rememberCoroutineScope()
    val showSnackbar: (String) -> Unit = remember(snackbarHostState) {
        { message ->
            snackbarScope.launch {
                snackbarHostState.currentSnackbarData?.dismiss()
                snackbarHostState.showSnackbar(message)
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (showMini || showBottomNav) {
                Column {
                    if (showMini) {
                        MiniPlayer(
                            controller = container.playerController,
                            onExpand = { navController.navigate(Routes.NOW_PLAYING) }
                        )
                    }
                    if (showBottomNav) {
                        MusarumBottomNav(
                            currentRoute = currentRoute,
                            onNavigate = { route ->
                                navController.navigate(route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        // Diálogo global de "actualización disponible": lo monitorizamos
        // aquí (fuera del NavHost) para que aparezca en cualquier pantalla.
        val update by container.updateChecker.available.collectAsState()
        update?.let { avail ->
            val ctx = androidx.compose.ui.platform.LocalContext.current
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {
                    // Solo cerrable si no es mandatory — así forzamos al
                    // usuario a actualizar cuando el backend lo exige.
                    if (!avail.mandatory) container.updateChecker.dismiss()
                },
                title = { androidx.compose.material3.Text("Nueva versión disponible") },
                text = {
                    androidx.compose.material3.Text(
                        "Hay una versión más reciente de Musarum (${avail.latest}). " +
                            "Tienes instalada la ${avail.current}."
                    )
                },
                confirmButton = {
                    androidx.compose.material3.TextButton(onClick = {
                        container.updateChecker.openDownload(ctx)
                        if (!avail.mandatory) container.updateChecker.dismiss()
                    }) {
                        androidx.compose.material3.Text("Descargar")
                    }
                },
                dismissButton = {
                    if (!avail.mandatory) {
                        androidx.compose.material3.TextButton(onClick = {
                            container.updateChecker.dismiss()
                        }) { androidx.compose.material3.Text("Más tarde") }
                    }
                }
            )
        }
        CompositionLocalProvider(LocalSnackbar provides showSnackbar) {
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            composable(Routes.LOGIN) {
                LoginScreen(
                    onLoginSuccess = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.LOGIN) { inclusive = true }
                        }
                    }
                )
            }

            val doLogout: () -> Unit = {
                container.playerController.stop()
                container.favoriteRepository.clear()
                container.playlistRepository.clear()
                sessionViewModel.logout()
                navController.navigate(Routes.LOGIN) {
                    popUpTo(0) { inclusive = true }
                }
            }

            composable(Routes.HOME) {
                HomeScreen(
                    onOpenSongs = { navController.navigate(Routes.SONGS) },
                    onOpenAlbums = { navController.navigate(Routes.ALBUMS) },
                    onOpenArtists = { navController.navigate(Routes.ARTISTS) },
                    onOpenGenre = { id -> navController.navigate(Routes.genreDetail(id)) }
                )
            }

            composable(Routes.PROFILE) {
                ProfileScreen(
                    onLogout = {
                        container.userRepository.clear()
                        doLogout()
                    },
                    onOpenFolders = { navController.navigate(Routes.LIBRARY_FOLDERS) },
                    onOpenUsers = { navController.navigate(Routes.USERS_ADMIN) },
                    onOpenServerStats = { navController.navigate(Routes.SERVER_STATS) },
                    onOpenEqualizer = { navController.navigate(Routes.EQUALIZER) },
                    onOpenPublishUpdate = { navController.navigate(Routes.PUBLISH_UPDATE) }
                )
            }

            composable(Routes.LIBRARY_FOLDERS) {
                LibraryFoldersScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.USERS_ADMIN) {
                UsersScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.SERVER_STATS) {
                ServerStatsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.PUBLISH_UPDATE) {
                com.musarum.mobile.profile.version.PublishUpdateScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.EQUALIZER) {
                EqualizerScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.QUEUE) {
                com.musarum.mobile.player.QueueScreen(
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SONGS) {
                SongsScreen()
            }

            composable(Routes.FAVORITES) {
                FavoritesScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.DOWNLOADS) {
                DownloadsScreen(onBack = { navController.popBackStack() })
            }

            composable(Routes.LIBRARY) {
                LibraryScreen(
                    onOpenPlaylist = { id -> navController.navigate(Routes.playlistDetail(id)) },
                    onOpenFavorites = { navController.navigate(Routes.FAVORITES) },
                    onOpenDownloads = { navController.navigate(Routes.DOWNLOADS) }
                )
            }

            composable(
                route = Routes.PLAYLIST_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStack ->
                val id = backStack.arguments?.getLong("id") ?: return@composable
                PlaylistDetailScreen(
                    playlistId = id,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.ALBUMS) {
                AlbumsScreen(onOpenAlbum = { id ->
                    navController.navigate(Routes.albumDetail(id))
                })
            }

            composable(
                route = Routes.ALBUM_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStack ->
                val id = backStack.arguments?.getLong("id") ?: return@composable
                AlbumDetailScreen(
                    albumId = id,
                    onBack = { navController.popBackStack() },
                    onOpenArtist = { artistId ->
                        navController.navigate(Routes.artistDetail(artistId))
                    }
                )
            }

            composable(Routes.ARTISTS) {
                ArtistsScreen(onOpenArtist = { id ->
                    navController.navigate(Routes.artistDetail(id))
                })
            }

            composable(
                route = Routes.ARTIST_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStack ->
                val id = backStack.arguments?.getLong("id") ?: return@composable
                ArtistDetailScreen(
                    artistId = id,
                    onBack = { navController.popBackStack() },
                    onOpenAlbum = { albumId ->
                        navController.navigate(Routes.albumDetail(albumId))
                    }
                )
            }

            composable(
                route = Routes.GENRE_DETAIL,
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { backStack ->
                val id = backStack.arguments?.getLong("id") ?: return@composable
                GenreDetailScreen(
                    genreId = id,
                    onBack = { navController.popBackStack() }
                )
            }

            composable(Routes.SEARCH) {
                SearchScreen(
                    onOpenAlbum = { id -> navController.navigate(Routes.albumDetail(id)) },
                    onOpenArtist = { id -> navController.navigate(Routes.artistDetail(id)) }
                )
            }

            composable(
                Routes.NOW_PLAYING,
                enterTransition = { playerEnter() },
                exitTransition = { playerExit() },
                popEnterTransition = { playerPopEnter() },
                popExitTransition = { playerPopExit() }
            ) {
                NowPlayingScreen(
                    onBack = { navController.popBackStack() },
                    onOpenLyrics = {
                        navController.navigate(Routes.LYRICS) {
                            popUpTo(Routes.NOW_PLAYING) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenInfo = {
                        navController.navigate(Routes.INFO) {
                            popUpTo(Routes.NOW_PLAYING) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenVideo = {
                        navController.navigate(Routes.VIDEO) {
                            popUpTo(Routes.NOW_PLAYING) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenFavorites = {
                        navController.navigate(Routes.FAVORITES) {
                            launchSingleTop = true
                        }
                    },
                    onOpenQueue = {
                        navController.navigate(Routes.QUEUE) {
                            launchSingleTop = true
                        }
                    },
                    onOpenArtist = { id -> navController.navigate(Routes.artistDetail(id)) }
                )
            }

            composable(
                Routes.LYRICS,
                enterTransition = { playerEnter() },
                exitTransition = { playerExit() },
                popEnterTransition = { playerPopEnter() },
                popExitTransition = { playerPopExit() }
            ) {
                LyricsScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTrack = {
                        navController.popBackStack(Routes.NOW_PLAYING, inclusive = false)
                    },
                    onOpenInfo = {
                        navController.navigate(Routes.INFO) {
                            popUpTo(Routes.NOW_PLAYING) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenVideo = {
                        navController.navigate(Routes.VIDEO) {
                            popUpTo(Routes.NOW_PLAYING) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                Routes.INFO,
                enterTransition = { playerEnter() },
                exitTransition = { playerExit() },
                popEnterTransition = { playerPopEnter() },
                popExitTransition = { playerPopExit() }
            ) {
                InfoScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTrack = {
                        navController.popBackStack(Routes.NOW_PLAYING, inclusive = false)
                    },
                    onOpenLyrics = {
                        navController.navigate(Routes.LYRICS) {
                            popUpTo(Routes.NOW_PLAYING) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenVideo = {
                        navController.navigate(Routes.VIDEO) {
                            popUpTo(Routes.NOW_PLAYING) { inclusive = false }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable(
                Routes.VIDEO,
                enterTransition = { playerEnter() },
                exitTransition = { playerExit() },
                popEnterTransition = { playerPopEnter() },
                popExitTransition = { playerPopExit() }
            ) {
                VideoScreen(
                    onBack = { navController.popBackStack() },
                    onOpenTrack = {
                        navController.popBackStack(Routes.NOW_PLAYING, inclusive = false)
                    },
                    onOpenLyrics = {
                        navController.navigate(Routes.LYRICS) {
                            popUpTo(Routes.NOW_PLAYING) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenInfo = {
                        navController.navigate(Routes.INFO) {
                            popUpTo(Routes.NOW_PLAYING) { inclusive = false }
                            launchSingleTop = true
                        }
                    },
                    onOpenArtist = { id -> navController.navigate(Routes.artistDetail(id)) }
                )
            }
        }
        }
    }
}

// ── Transiciones entre pantallas del player ────────────────────────────────
// Las 4 pantallas (Audio, Letra, Info, Vídeo) comparten la misma píldora de
// arriba. Las transicionamos con un slide horizontal pequeño + fade en 300 ms:
// la nueva entra desde la derecha (o izquierda) según el orden lógico de la
// píldora, no según el stack de navegación — así el movimiento acompaña al
// highlight que corre dentro de la píldora en vez de ir en contra.
//
// Orden lógico (de izquierda a derecha, como se pintan en la píldora):
//   LYRICS · VIDEO · NOW_PLAYING · INFO

private const val PILL_ANIM_MS = 300

private val playerOrder = listOf(
    Routes.LYRICS,
    Routes.VIDEO,
    Routes.NOW_PLAYING,
    Routes.INFO
)

private fun AnimatedContentTransitionScope<NavBackStackEntry>.directionTowards(): SlideDirection {
    val from = playerOrder.indexOf(initialState.destination.route)
    val to   = playerOrder.indexOf(targetState.destination.route)
    // Si cualquiera no está en la lista (venimos de fuera del player, p. ej.
    // el MiniPlayer), usamos "sube desde abajo" como fallback neutro.
    if (from < 0 || to < 0) return SlideDirection.Up
    return if (to > from) SlideDirection.Left else SlideDirection.Right
}

private fun AnimatedContentTransitionScope<NavBackStackEntry>.playerEnter() =
    slideIntoContainer(
        towards = directionTowards(),
        animationSpec = tween(PILL_ANIM_MS, easing = FastOutSlowInEasing)
    ) + fadeIn(tween(PILL_ANIM_MS, easing = FastOutSlowInEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.playerExit() =
    slideOutOfContainer(
        towards = directionTowards(),
        animationSpec = tween(PILL_ANIM_MS, easing = FastOutSlowInEasing)
    ) + fadeOut(tween(PILL_ANIM_MS, easing = FastOutSlowInEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.playerPopEnter() = playerEnter()

private fun AnimatedContentTransitionScope<NavBackStackEntry>.playerPopExit() = playerExit()
