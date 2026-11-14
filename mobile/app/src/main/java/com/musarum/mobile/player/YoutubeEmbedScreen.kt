package com.musarum.mobile.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.musarum.mobile.LocalAppContainer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.YouTubePlayer
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.listeners.AbstractYouTubePlayerListener
import com.pierfrancescosoffritti.androidyoutubeplayer.core.player.views.YouTubePlayerView

/**
 * Reproductor de YouTube embebido dentro de la app usando la librería
 * {@code com.pierfrancescosoffritti.androidyoutubeplayer}. El videoId se
 * resuelve previamente en el backend (scraping ligero de
 * youtube.com/results + caché en {@code songs.youtube_video_id}) y se pasa
 * como argumento de navegación — así la pantalla no tarda en cargar.
 *
 * Al entrar pausamos el player de Musarum para que no suene a la vez.
 * Al salir el usuario decide si retomar (tapea play) o seguir con el
 * vídeo cerrado. No restauramos automáticamente: forzar play al volver
 * sería intrusivo si venía pausado a propósito.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YoutubeEmbedScreen(
    songId: Long,
    videoId: String,
    songTitle: String?,
    onBack: () -> Unit
) {
    val container = LocalAppContainer.current
    val context = LocalContext.current

    // Al abrir la pantalla pausamos el audio de Musarum. Si ya estaba pausado,
    // togglePlayPause lo encendería — chequeamos antes.
    DisposableEffect(songId) {
        val wasPlaying = container.playerController.state.value.isPlaying
        if (wasPlaying) container.playerController.togglePlayPause()
        onDispose { }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        songTitle ?: "Vídeo",
                        maxLines = 1,
                        style = MaterialTheme.typography.titleMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                actions = {
                    // Botón "abrir en la app oficial" por si el embed falla o
                    // el usuario quiere pantalla completa / comentarios.
                    IconButton(onClick = {
                        runCatching {
                            val intent = android.content.Intent(
                                android.content.Intent.ACTION_VIEW,
                                android.net.Uri.parse("https://www.youtube.com/watch?v=$videoId")
                            ).apply {
                                addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                            }
                            context.startActivity(intent)
                        }
                    }) {
                        Icon(Icons.Filled.OpenInNew, contentDescription = "Abrir en YouTube")
                    }
                }
            )
        }
    ) { inner ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(inner)
        ) {
            // AndroidView envuelve el YouTubePlayerView clásico. Factor 16:9
            // ocupa ancho completo con la altura calculada; debajo dejamos
            // espacio para posibles acciones (compartir, etc).
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16f / 9f)
            ) {
                AndroidView(
                    factory = { ctx ->
                        YouTubePlayerView(ctx).apply {
                            addYouTubePlayerListener(object : AbstractYouTubePlayerListener() {
                                override fun onReady(youTubePlayer: YouTubePlayer) {
                                    // loadVideo arranca autoplay; usar cueVideo
                                    // en su lugar deja el thumbnail hasta que
                                    // el usuario tape el play. Aquí queremos
                                    // autoplay porque el usuario vino aquí a
                                    // VER el vídeo.
                                    youTubePlayer.loadVideo(videoId, 0f)
                                }
                            })
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                    // Liberar el player al salir de la pantalla es crítico:
                    // si no, el WebView interno sigue sonando en background.
                    onRelease = { it.release() }
                )
            }

            Spacer(Modifier.height(16.dp))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "Vídeo de YouTube · ID $videoId",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                TextButton(onClick = onBack) {
                    Text("Cerrar vídeo y volver a la música")
                }
            }
        }
    }
}
