package com.musarum.mobile.player

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State

/**
 * Pulso visual al ritmo del BPM detectado — el cover del player "late"
 * sincronizado al compás. Devuelve un {@code State<Float>} que oscila
 * entre 1.0f (reposo) y un máximo discreto, a la cadencia del tempo.
 *
 * Diseño:
 * <ul>
 *   <li>Amplitud {@code 1.025f} — perceptible pero sin marear. Más y se
 *       nota "nervioso"; menos y no se nota en absoluto.</li>
 *   <li>Easing {@code FastOutSlowInEasing} — expansión rápida al beat,
 *       contracción lenta: así cada tic se siente como un latido.</li>
 *   <li>Si {@code bpm} está fuera del rango útil (40..240) o {@code isPlaying}
 *       es false, el target cae a 1.0f y el cover se queda quieto — sin
 *       pulso cuando no toca.</li>
 * </ul>
 *
 * El periodo por beat se calcula como {@code 60_000 / bpm} ms; usamos la
 * mitad del periodo como {@code durationMillis} del tween y
 * {@code RepeatMode.Reverse} para que cada ciclo completo (ida + vuelta)
 * equivalga a un beat.
 */
@Composable
fun rememberBeatScale(bpm: Int?, isPlaying: Boolean): State<Float> {
    // Smart-cast: validBpm es el BPM útil (o null si no se pulsa).
    val validBpm = bpm?.takeIf { isPlaying && it in 40..240 }
    val shouldPulse = validBpm != null
    val halfPeriodMs = validBpm?.let { (60_000 / it) / 2 } ?: 500

    val transition = rememberInfiniteTransition(label = "beatPulse")
    return transition.animateFloat(
        initialValue = 1f,
        targetValue = if (shouldPulse) 1.025f else 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = halfPeriodMs, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beatScale"
    )
}
