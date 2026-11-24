package com.musarum.mobile.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/**
 * Píldora de modos del player. Diseño compacto: tabs cuadrados (44dp) con
 * SOLO icono — más pequeña que la versión con texto, deja respirar la
 * cabecera del NowPlaying. La píldora ya no ocupa toda la fila: se envuelve
 * en un contenedor que la centra, así a su lado (izq/der) caben el chevron
 * de "bajar" y el menú "...".
 *
 * El highlight ámbar se DESLIZA entre tabs en 280 ms con `animateDpAsState`
 * sobre un offset relativo a la posición del seleccionado. El tinte del
 * icono interpola onSurface↔onPrimary al ritmo del highlight.
 */
enum class PlayerMode { TRACK, LYRICS, INFO, VIDEO }

private const val PILL_ANIM_MS = 280
// Píldora compacta — estilo YT Music. Antes 44dp; ahora 36dp para dejar
// más aire a la cabecera del NowPlaying. Icono 18dp dentro.
private val PILL_HEIGHT = 36.dp
private val PILL_TAB_WIDTH = 40.dp
private val PILL_INNER_PADDING = 2.dp
private val PILL_ICON_SIZE = 18.dp

private data class PillTab(val mode: PlayerMode, val icon: ImageVector, val label: String)

@Composable
fun ModePill(
    selected: PlayerMode,
    onSelect: (PlayerMode) -> Unit,
    modifier: Modifier = Modifier,
    showVideo: Boolean = false
) {
    // Orden fijo: Letra · [Vídeo opcional] · Audio · Info.
    val items = buildList {
        add(PillTab(PlayerMode.LYRICS, Icons.Filled.Lyrics, "Letra"))
        if (showVideo) add(PillTab(PlayerMode.VIDEO, Icons.Filled.Videocam, "Vídeo"))
        add(PillTab(PlayerMode.TRACK, Icons.Filled.Audiotrack, "Audio"))
        add(PillTab(PlayerMode.INFO, Icons.Filled.Info, "Info"))
    }
    val selectedIndex = items.indexOfFirst { it.mode == selected }.coerceAtLeast(0)

    val highlightOffset by animateDpAsState(
        targetValue = PILL_TAB_WIDTH * selectedIndex,
        animationSpec = tween(PILL_ANIM_MS, easing = FastOutSlowInEasing),
        label = "modepill-offset"
    )

    val pillShape = RoundedCornerShape(50)
    val borderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)

    // Sin shadow: la elevación es lo que la hace "cantar" sobre el gradient
    // del shell. Carlos quiere algo más discreto tipo YT Music — solo un
    // borde finito y el fondo semi-transparente.
    Surface(
        shape = pillShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f),
        modifier = modifier
            .border(width = 1.dp, color = borderColor, shape = pillShape)
            .height(PILL_HEIGHT)
    ) {
        Box(
            modifier = Modifier
                .padding(PILL_INNER_PADDING)
                .fillMaxHeight()
        ) {
            // Highlight ámbar bajo los iconos (se dibuja antes para quedar detrás).
            Box(
                modifier = Modifier
                    .padding(start = highlightOffset)
                    .width(PILL_TAB_WIDTH)
                    .fillMaxHeight()
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
            )

            Row(modifier = Modifier.fillMaxHeight()) {
                val onSurface = MaterialTheme.colorScheme.onSurface
                val onPrimary = MaterialTheme.colorScheme.onPrimary
                items.forEachIndexed { index, tab ->
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
                            .clickable { onSelect(tab.mode) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = lerp(onSurface, onPrimary, tint),
                            modifier = Modifier.size(PILL_ICON_SIZE)
                        )
                    }
                }
            }
        }
    }
}
