package com.musarum.mobile.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp

/**
 * Píldora de modos del player. Diseño compacto: anchos fijos por tab (72dp)
 * para que la píldora se adapte al número de modos (3 o 4) sin ocupar toda
 * la pantalla. Fondo sólido con borde sutil para que destaque sobre el
 * gradient dinámico del shell.
 *
 * El highlight ámbar se DESLIZA entre tabs en 280 ms con `animateDpAsState`
 * sobre un offset relativo a la posición del seleccionado. Los textos
 * interpolan color onSurface↔onPrimary conforme el highlight entra/sale.
 */
enum class PlayerMode { TRACK, LYRICS, INFO, VIDEO }

private const val PILL_ANIM_MS = 280
private val PILL_HEIGHT = 34.dp
private val PILL_TAB_WIDTH = 68.dp
private val PILL_INNER_PADDING = 3.dp

@Composable
fun ModePill(
    selected: PlayerMode,
    onSelect: (PlayerMode) -> Unit,
    modifier: Modifier = Modifier,
    showVideo: Boolean = false
) {
    // Orden fijo: Letra · [Vídeo opcional] · Audio · Info.
    val items = buildList {
        add(PlayerMode.LYRICS to "Letra")
        if (showVideo) add(PlayerMode.VIDEO to "Vídeo")
        add(PlayerMode.TRACK to "Audio")
        add(PlayerMode.INFO to "Info")
    }
    val selectedIndex = items.indexOfFirst { it.first == selected }.coerceAtLeast(0)

    // Highlight animado: se mueve en pasos de PILL_TAB_WIDTH.
    val highlightOffset by animateDpAsState(
        targetValue = PILL_TAB_WIDTH * selectedIndex,
        animationSpec = tween(PILL_ANIM_MS, easing = FastOutSlowInEasing),
        label = "modepill-offset"
    )

    val pillShape = RoundedCornerShape(50)
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f)

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = pillShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
            modifier = Modifier
                .shadow(elevation = 4.dp, shape = pillShape, clip = false)
                .border(width = 1.dp, color = borderColor, shape = pillShape)
                .height(PILL_HEIGHT)
        ) {
            Box(
                modifier = Modifier
                    .padding(PILL_INNER_PADDING)
                    .fillMaxHeight()
            ) {
                // Highlight ámbar bajo los textos, posicionado absolutamente.
                // Se dibuja antes para quedar detrás.
                Box(
                    modifier = Modifier
                        .padding(start = highlightOffset)
                        .width(PILL_TAB_WIDTH)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )

                // Fila de tabs clicables sobre el highlight. Textos
                // interpolan color según el highlight debajo.
                Row(modifier = Modifier.fillMaxHeight()) {
                    val onSurface = MaterialTheme.colorScheme.onSurface
                    val onPrimary = MaterialTheme.colorScheme.onPrimary
                    items.forEachIndexed { index, (mode, label) ->
                        val isSel = index == selectedIndex
                        val tint by animateFloatAsState(
                            targetValue = if (isSel) 1f else 0f,
                            animationSpec = tween(PILL_ANIM_MS, easing = FastOutSlowInEasing),
                            label = "modepill-tint-$index"
                        )
                        Box(
                            modifier = Modifier
                                .width(PILL_TAB_WIDTH)
                                .fillMaxHeight()
                                .clip(CircleShape)
                                .clickable { onSelect(mode) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                label,
                                style = MaterialTheme.typography.labelMedium,
                                color = lerp(onSurface, onPrimary, tint)
                            )
                        }
                    }
                }
            }
        }
    }
}
