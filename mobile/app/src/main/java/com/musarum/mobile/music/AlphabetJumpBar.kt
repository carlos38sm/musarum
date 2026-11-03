package com.musarum.mobile.music

import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Columna vertical A–Z + "#" en el borde derecho. Al pulsar o arrastrar
 * salta al índice absoluto donde empieza esa letra en la lista completa.
 *
 * Recibe directamente el mapa {letra → offset absoluto} que precomputa el
 * backend (/songs/letter-offsets). Antes la calculaba en el móvil a partir
 * de la lista entera, lo cual era inviable con paginación: con 7000
 * canciones no las tienes todas cargadas.
 */
@Composable
fun AlphabetJumpBar(
    letterOffsets: Map<String, Int>,
    onJump: (absoluteIndex: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    if (letterOffsets.isEmpty()) return
    val letters = ('A'..'Z').toList() + '#'
    var totalHeight by remember { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .width(22.dp)
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .onGloballyPositioned { totalHeight = it.size.height }
            .pointerInput(letterOffsets) {
                detectVerticalDragGestures { change, _ ->
                    val y = change.position.y.coerceIn(0f, totalHeight.toFloat())
                    val slot = ((y / totalHeight) * letters.size).toInt()
                        .coerceIn(0, letters.size - 1)
                    val letter = letters[slot].toString()
                    letterOffsets[letter]?.let(onJump) ?: run {
                        // No hay canciones con esa inicial — buscamos la más
                        // cercana hacia abajo (y si no, hacia arriba) para
                        // que el gesto no quede mudo.
                        val fallback = letters.drop(slot + 1)
                            .firstNotNullOfOrNull { letterOffsets[it.toString()] }
                            ?: letters.take(slot).reversed()
                                .firstNotNullOfOrNull { letterOffsets[it.toString()] }
                        fallback?.let(onJump)
                    }
                }
            },
        verticalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
    ) {
        letters.forEach { letter ->
            val hasEntry = letterOffsets.containsKey(letter.toString())
            Text(
                letter.toString(),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                fontSize = 10.sp,
                color = if (hasEntry) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f),
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .width(22.dp)
                    .pointerInput(letter, hasEntry) {
                        awaitPointerEventScope {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull() ?: continue
                                if (change.pressed && !change.previousPressed && hasEntry) {
                                    letterOffsets[letter.toString()]?.let(onJump)
                                }
                            }
                        }
                    }
            )
        }
    }
}
