package com.musarum.mobile.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.music.CoverImage
import com.musarum.mobile.player.rememberDominantCoverColor

/**
 * Modo coche: UI simplificada con botones gigantes para controlar la
 * reproducción con una mano al volante sin mirar demasiado. 3 controles:
 * anterior / play-pause (grande) / siguiente. Título + artista grandes
 * arriba, cover sin detalles. Nada de listas, letras, menús.
 *
 * El fondo toma el color dominante de la portada (mismo mecanismo que
 * NowPlaying) para que el conductor no vea un contraste brusco si cambia
 * de la pantalla principal a esta.
 */
@Composable
fun CarModeScreen(
    onExit: () -> Unit
) {
    val container = LocalAppContainer.current
    val state by container.playerController.state.collectAsState()
    val song = state.currentSong
    val fallback = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
    val dominant by rememberDominantCoverColor(
        coverUrl = state.currentCoverUrl,
        fallback = fallback
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0f to dominant.copy(alpha = 0.60f),
                    1f to MaterialTheme.colorScheme.background
                )
            )
    ) {
        // Botón de salida — arriba izquierda, tamaño generoso.
        IconButton(
            onClick = onExit,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(24.dp)
                .size(56.dp)
        ) {
            Icon(
                Icons.Filled.Close,
                contentDescription = "Salir del modo coche",
                tint = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.size(36.dp)
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 32.dp, vertical = 48.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Portada grande centrada.
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.Transparent,
                modifier = Modifier
                    .fillMaxWidth(0.65f)
                    .aspectRatio(1f)
            ) {
                CoverImage(
                    url = state.currentCoverUrl,
                    size = 0.dp,  // ignorado por fillMaxSize interno
                    cornerRadius = 16.dp,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(Modifier.height(36.dp))

            // Título y artista grandes para ser legibles de reojo.
            Text(
                text = song?.title ?: "—",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 38.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = song?.artistsLabel.orEmpty(),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 24.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(56.dp))

            // Controles: 3 botones gigantes centrados.
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(28.dp)
            ) {
                CarButton(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = "Anterior",
                    onClick = { container.playerController.previous() }
                )
                // Play/pause es el principal — más grande que los lados.
                CarButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pausa" else "Reproducir",
                    size = 128.dp,
                    iconSize = 72.dp,
                    primary = true,
                    onClick = { container.playerController.togglePlayPause() }
                )
                CarButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = "Siguiente",
                    onClick = { container.playerController.next() }
                )
            }
        }
    }
}

@Composable
private fun CarButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    size: androidx.compose.ui.unit.Dp = 88.dp,
    iconSize: androidx.compose.ui.unit.Dp = 48.dp,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (primary) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surface
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = bg,
        modifier = Modifier.size(size)
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = fg,
                modifier = Modifier.size(iconSize)
            )
        }
    }
}
