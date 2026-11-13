package com.musarum.mobile.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.musarum.mobile.music.CoverImage
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * Mini-player tipo pastilla flotante (foto 5 de referencias).
 * Esquinas muy redondeadas, cover a la izquierda, título + artista en el
 * centro, play y next a la derecha. Debajo una barra fina de progreso.
 */
@Composable
fun MiniPlayer(
    controller: PlayerController,
    onExpand: () -> Unit
) {
    val state by controller.state.collectAsState()
    val song = state.currentSong ?: return

    val progress = if (state.durationMs > 0) {
        (state.positionMs.toFloat() / state.durationMs.toFloat()).coerceIn(0f, 1f)
    } else 0f

    // Offset horizontal: la fila sigue al dedo mientras arrastras. Al soltar,
    // si pasa umbral animamos fuera + dispara next/prev; si no, spring vuelve
    // a 0. El alpha baja con el arrastre para dar sensación de "paso de
    // página". Al cambiar de canción el estado se refresca y la nueva row
    // aparece en el centro sin flash.
    val scope = rememberCoroutineScope()
    var dragX by remember { mutableFloatStateOf(0f) }
    val fadeDistancePx = with(androidx.compose.ui.platform.LocalDensity.current) { 180.dp.toPx() }
    val rowAlpha = (1f - (abs(dragX) / fadeDistancePx).coerceIn(0f, 0.6f))

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        // Dejamos el Surface transparente: el fondo con blur de la portada
        // se pinta dentro del Box de abajo. Así tenemos degradé suave de la
        // canción actual tras los textos y el play, pero el contorno del
        // Surface + shadowElevation + clip a RoundedCornerShape siguen
        // definiendo la pastilla.
        color = androidx.compose.ui.graphics.Color.Transparent,
        shadowElevation = 10.dp
    ) {
        Box(modifier = Modifier.clip(RoundedCornerShape(22.dp))) {
            // Fondo blur de la portada actual. Si no hay cover, fallback a
            // surfaceVariant plano para mantener contraste sobre el fondo.
            val cover = state.currentCoverUrl
            if (cover != null) {
                AsyncImage(
                    model = cover,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .blur(28.dp)
                )
                // Velo para asegurar legibilidad de textos y controles: el
                // blur solo no basta si la portada es muy clara. Degradé
                // vertical sutil para no aplastar completamente los colores.
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            Brush.verticalGradient(
                                0f to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.78f),
                                1f to MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.92f)
                            )
                        )
                )
            } else {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                )
            }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                // Swipe horizontal sobre la pastilla → siguiente / anterior.
                // El gesto se captura en toda la fila (incluidos los botones)
                // pero el offset visual + fade se aplican SOLO al bloque
                // cover+título+artista. Los controles (play, siguiente) se
                // quedan fijos como en Spotify / Apple Music.
                .pointerInput(Unit) {
                    val threshold = 90.dp.toPx()
                    val flyAwayPx = 320.dp.toPx()
                    detectHorizontalDragGestures(
                        onDragEnd = {
                            when {
                                dragX < -threshold -> scope.launch {
                                    val a = Animatable(dragX)
                                    a.animateTo(-flyAwayPx, tween(160)) { dragX = value }
                                    controller.next()
                                    dragX = 0f
                                }
                                dragX > threshold -> scope.launch {
                                    val a = Animatable(dragX)
                                    a.animateTo(flyAwayPx, tween(160)) { dragX = value }
                                    controller.previous()
                                    dragX = 0f
                                }
                                else -> scope.launch {
                                    val a = Animatable(dragX)
                                    a.animateTo(0f, spring()) { dragX = value }
                                }
                            }
                        },
                        onDragCancel = {
                            scope.launch {
                                val a = Animatable(dragX)
                                a.animateTo(0f, spring()) { dragX = value }
                            }
                        }
                    ) { _, dx -> dragX += dx }
                }
                .clickable(onClick = onExpand)
                .padding(start = 8.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Bloque desplazable: cover + título + artista. weight(1f) para
            // ocupar lo que haya hasta los botones. El offset lo mueve con
            // el dedo; alpha baja según el arrastre.
            Row(
                modifier = Modifier
                    .weight(1f)
                    .offset { IntOffset(dragX.toInt(), 0) }
                    .alpha(rowAlpha),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoverImage(
                    url = state.currentCoverUrl,
                    size = 44.dp,
                    cornerRadius = 12.dp
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        song.artistsLabel,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            // Play con progreso circular alrededor — reemplaza la barra lineal.
            // El anillo va en color tertiary (rosa cálido) para resaltar sobre
            // el oro del botón y marcar bien la posición de la canción.
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.tertiary,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f),
                    strokeWidth = 3.dp,
                    gapSize = 0.dp,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    IconButton(
                        onClick = { controller.togglePlayPause() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                            modifier = Modifier.size(20.dp),
                            tint = MaterialTheme.colorScheme.onPrimary
                        )
                    }
                }
            }
            IconButton(
                onClick = { controller.next() },
                enabled = state.hasNext
            ) {
                Icon(
                    Icons.Filled.SkipNext,
                    contentDescription = "Siguiente",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        }
    }
}
