package com.musarum.mobile.player

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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.musarum.mobile.LocalAppContainer

/**
 * Modo coche: prioridad total a los controles. Sin portada, sin mini-player.
 * Carlos: "yo priorizarÃ­a los botones, no la portada como tal, y el
 * miniplayer quizÃ¡s hasta lo quitaba de la vista de modo coche, asÃ­ hay
 * mÃ¡s espacio para botones". Layout vertical: tÃ­tulo + artista ARRIBA
 * (fuera del alcance del pulgar), y los 3 botones APILADOS ocupando casi
 * toda la pantalla â€” mÃ¡xima Ã¡rea tÃ¡ctil para conducir sin mirar.
 *
 * El fondo toma el color dominante de la portada para no hacer salto
 * visual con el NowPlaying, pero la portada en sÃ­ no se pinta.
 */
@Composable
fun CarModeScreen(
    onExit: () -> Unit
) {
    val container = LocalAppContainer.current
    val state by container.playerController.state.collectAsStateWithLifecycle()
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
        // BotÃ³n de salida â€” arriba izquierda, tamaÃ±o generoso.
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
                .padding(start = 28.dp, end = 28.dp, top = 100.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // TÃ­tulo y artista arriba del todo. Compactos â€” todo el espacio
            // vertical disponible se lo quedan los botones.
            Text(
                text = song?.title ?: "â€”",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 32.sp,
                    fontWeight = FontWeight.ExtraBold
                ),
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = song?.artistsLabel.orEmpty(),
                style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(32.dp))

            // Botones apilados en vertical â€” cada uno enorme. Ratio: play
            // central mÃ¡s grande (es el mÃ¡s usado), prev/next iguales. Al
            // ser verticales, el pulgar del volante los encuentra solos sin
            // tener que mirar a lados distintos de la pantalla.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CarVerticalButton(
                    icon = Icons.Filled.SkipPrevious,
                    contentDescription = "Anterior",
                    label = "Anterior",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClick = { container.playerController.previous() }
                )
                CarVerticalButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    contentDescription = if (state.isPlaying) "Pausa" else "Reproducir",
                    label = if (state.isPlaying) "Pausa" else "Reproducir",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1.3f),  // un pelÃ­n mÃ¡s alto por ser el principal
                    primary = true,
                    onClick = { container.playerController.togglePlayPause() }
                )
                CarVerticalButton(
                    icon = Icons.Filled.SkipNext,
                    contentDescription = "Siguiente",
                    label = "Siguiente",
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    onClick = { container.playerController.next() }
                )
            }
        }
    }
}

/**
 * BotÃ³n "tipo banda" ancho que ocupa toda la fila. Icono grande centrado
 * + etiqueta grande debajo. MÃ¡s fÃ¡cil de dar con el pulgar al volante
 * que un cÃ­rculo pequeÃ±o â€” el toque queda en cualquier punto de la banda.
 */
@Composable
private fun CarVerticalButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    label: String,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (primary) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = bg,
        contentColor = fg,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = fg,
                modifier = Modifier.size(72.dp)
            )
            Spacer(Modifier.size(20.dp))
            Text(
                label,
                style = MaterialTheme.typography.displaySmall.copy(
                    fontSize = 30.sp,
                    fontWeight = FontWeight.SemiBold
                ),
                color = fg
            )
        }
    }
}
