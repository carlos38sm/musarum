package com.musarum.mobile.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/**
 * Superficie "glass": fondo blanco translúcido con un borde más blanco aún
 * para simular reflejo de cristal. Portado del musarum viejo, útil para
 * tarjetas que flotan sobre un fondo con color (p. ej. hero de álbum con
 * blur). No aplica blur por sí misma — el blur se compone antes en el padre.
 */
@Composable
fun GlassSurface(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(16.dp),
    alpha: Float = 0.10f,
    borderAlpha: Float = 0.15f,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(Color.White.copy(alpha = alpha))
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = borderAlpha),
                shape = shape
            )
    ) {
        content()
    }
}
