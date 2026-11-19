package com.musarum.mobile.player

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.musarum.mobile.music.CoverImage
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
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

    // DOS colores distintos de la portada: el dominante (primary) y uno
    // con buen contraste de hue (secondary). Antes usábamos 3 tonos del
    // mismo color y Carlos no veía diferencia entre fondo, anillo y botón.
    // Ahora: fondo = primary, anillo = secondary (contraste real), play
    // bg = secondary oscurecido para que el anillo salte visualmente por
    // encima del play.
    val fallbackPrimary = MaterialTheme.colorScheme.tertiary
    val fallbackSecondary = MaterialTheme.colorScheme.primary
    val coverPair by rememberCoverColorPair(
        coverUrl = state.currentCoverUrl,
        fallbackPrimary = fallbackPrimary,
        fallbackSecondary = fallbackSecondary
    )
    val dominant = coverPair.primary
    val accent = coverPair.secondary
    val isDark = dominant.luminance() < 0.35f
    val lighter = if (isDark) dominant.lighten(0.20f) else dominant.lighten(0.08f)
    val darker = if (isDark) dominant.darken(0.18f) else dominant.darken(0.32f)
    // Degradado animado: dos focos que giran lentamente alrededor del
    // centro del mini-player. 12 s por vuelta para que sea perceptible pero
    // no distraiga.
    val gradientTransition = rememberInfiniteTransition(label = "mpGrad")
    val angle by gradientTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 12_000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "mpAngle"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        shape = RoundedCornerShape(22.dp),
        // Dejamos el Surface transparente: el fondo coloreado dinámico se
        // pinta dentro del Box de abajo. Así tenemos degradé suave que se
        // mueve lentamente, con el color dominante de la canción.
        color = Color.Transparent,
        shadowElevation = 10.dp
    ) {
        Box(modifier = Modifier.clip(RoundedCornerShape(22.dp))) {
            // Fondo dinámico: base plana con el color dominante + dos
            // "manchas" radiales de color más claro/oscuro que orbitan. No
            // es la imagen ni blur: es color extraído del cover, menos
            // ruido visual y más definición. Sin cover, fallback a tertiary.
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(dominant.copy(alpha = 0.96f))
            )
            // Mancha 1: se mueve en círculo pequeño.
            val radRad = Math.toRadians(angle.toDouble())
            val cx1 = 0.5f + 0.25f * cos(radRad).toFloat()
            val cy1 = 0.5f + 0.25f * sin(radRad).toFloat()
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(lighter.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(cx1 * 1000f, cy1 * 200f),
                            radius = 600f
                        )
                    )
            )
            // Mancha 2: contra-fase, otro color.
            val radRad2 = Math.toRadians((angle + 180f).toDouble())
            val cx2 = 0.5f + 0.25f * cos(radRad2).toFloat()
            val cy2 = 0.5f + 0.25f * sin(radRad2).toFloat()
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(
                        Brush.radialGradient(
                            colors = listOf(darker.copy(alpha = 0.45f), Color.Transparent),
                            center = Offset(cx2 * 1000f, cy2 * 200f),
                            radius = 600f
                        )
                    )
            )
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

            // Anillo = secondary (color DIFERENTE del dominante del fondo,
            // con contraste de hue garantizado por rememberCoverColorPair).
            // Botón play = secondary oscurecido, así el anillo salta
            // visualmente sobre el fondo y sobre el botón a la vez.
            val ringColor = accent
            val playBgColor = accent.darken(if (accent.luminance() < 0.35f) 0.10f else 0.30f)
            // Si el fondo del botón es MUY oscuro (portadas tipo metal o
            // trap con negro/rojo oscuro), el símbolo play/pause en
            // onPrimary (negro) se pierde. Forzamos blanco en ese caso;
            // dejamos onPrimary (acorde al theme dinámico) para fondos
            // claros.
            val playIconTint = if (playBgColor.luminance() < 0.35f)
                Color.White
            else
                MaterialTheme.colorScheme.onPrimary
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(48.dp),
                    color = ringColor,
                    trackColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f),
                    strokeWidth = 2.dp,
                    gapSize = 0.dp,
                    strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
                )
                Surface(
                    modifier = Modifier.size(38.dp),
                    shape = CircleShape,
                    color = playBgColor
                ) {
                    IconButton(
                        onClick = { controller.togglePlayPause() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        Icon(
                            imageVector = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            contentDescription = if (state.isPlaying) "Pausar" else "Reproducir",
                            modifier = Modifier.size(20.dp),
                            tint = playIconTint
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

/** Aclara un Color mezclándolo con blanco por el factor [amount] (0..1). */
private fun Color.lighten(amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = red + (1f - red) * a,
        green = green + (1f - green) * a,
        blue = blue + (1f - blue) * a,
        alpha = alpha
    )
}

/** Oscurece un Color mezclándolo con negro por el factor [amount] (0..1). */
private fun Color.darken(amount: Float): Color {
    val a = amount.coerceIn(0f, 1f)
    return Color(
        red = red * (1f - a),
        green = green * (1f - a),
        blue = blue * (1f - a),
        alpha = alpha
    )
}
