package com.musarum.mobile.music

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import com.musarum.mobile.player.CamelotWheel

/**
 * Píldora Camelot "cristal": el código ("8A", "12B"...) pintado con un
 * gradient oblicuo de tres paradas en la gama de su propia tonalidad. Sin
 * fondo sólido invasivo — solo un sutil halo translúcido del mismo tono
 * para que el texto respire. El brillo lo da una Shadow suave debajo del
 * glyph.
 *
 * Cada cuadrante de la rueda Camelot (12 pasos de hue) mantiene su propia
 * paleta — igual que Mixed In Key — así el badge sigue aportando
 * información visual al que lee tonalidades, pero deja de "gritar".
 */
@Composable
fun CamelotBadge(musicalKey: String?, modifier: Modifier = Modifier) {
    val code = CamelotWheel.toCamelot(musicalKey) ?: return
    val palette = camelotPalette(code)

    val brush = Brush.linearGradient(
        colors = listOf(palette.light, palette.base, palette.dark),
        start = Offset(0f, 0f),
        end = Offset.Infinite
    )

    Text(
        text = code,
        style = TextStyle(
            brush = brush,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
            // Halo suave del propio tono para que resalte sobre fondos oscuros
            // sin necesidad de rectángulo opaco.
            shadow = Shadow(
                color = palette.base.copy(alpha = 0.55f),
                offset = Offset(0f, 1f),
                blurRadius = 6f
            )
        ),
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            // Fondo casi inexistente: solo un veladito del mismo tono para que
            // en fondos muy claros el texto no quede flotando sin anclar.
            .background(palette.base.copy(alpha = 0.08f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** Trio de tonos (claro / medio / oscuro) de la misma gama Camelot. */
private data class CamelotPalette(val light: Color, val base: Color, val dark: Color)

private fun camelotPalette(code: String): CamelotPalette {
    val digits = code.takeWhile { it.isDigit() }
    val num = digits.toIntOrNull() ?: return CamelotPalette(
        Color(0xFFBBBBBB), Color(0xFF888888), Color(0xFF555555)
    )
    val isMajor = code.getOrNull(digits.length)?.uppercaseChar() == 'B'
    // 12 pasos de hue de 30°, empezando por 1 = rojo cálido (igual que antes).
    val hue = ((num - 1) * 30f) % 360f
    // Mayor = más vivo (más saturado y claro); menor = más introspectivo.
    val sat = if (isMajor) 0.72f else 0.60f
    return CamelotPalette(
        light = Color.hsl(hue, sat, if (isMajor) 0.75f else 0.68f),
        base  = Color.hsl(hue, sat, if (isMajor) 0.58f else 0.50f),
        dark  = Color.hsl(hue, sat, if (isMajor) 0.42f else 0.35f)
    )
}
