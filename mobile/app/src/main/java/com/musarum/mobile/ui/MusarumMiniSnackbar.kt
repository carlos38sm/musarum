package com.musarum.mobile.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Snackbar en forma de píldora, pequeña y poco invasiva. Sustituye al
 * Snackbar grande de Material que ocupaba todo el ancho y tapaba los
 * controles del player en NowPlaying / Video.
 *
 * Se renderiza con padding horizontal reducido, texto {@code bodySmall},
 * 2 líneas máximo con elipsis, bordes muy redondeados (24 dp) y fondo
 * surfaceContainerHighest para que resalte sobre cualquier gradient del
 * player sin competir visualmente.
 */
@Composable
fun MusarumMiniSnackbar(data: SnackbarData, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.padding(horizontal = 24.dp),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 6.dp,
        shadowElevation = 6.dp
    ) {
        Text(
            text = data.visuals.message,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}
