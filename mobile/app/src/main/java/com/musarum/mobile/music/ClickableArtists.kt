package com.musarum.mobile.music

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import com.musarum.mobile.data.network.dto.ArtistRefDto

/**
 * Fila de nombres de artista donde cada uno es su propio enlace independiente.
 * Spotify-style: tap en "Shakira" → Shakira; tap en "Maluma" → Maluma. Sin
 * BottomSheet intermedio ni confusión. Si no cabe en una línea, usa FlowRow
 * para envolver sin cortar nombres por la mitad.
 *
 * `onArtistClick` recibe el id del artista pulsado. Si la lista está vacía
 * se pinta "Desconocido" como texto plano no clicable.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ClickableArtists(
    artists: List<ArtistRefDto>,
    onArtistClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.titleMedium,
    color: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Center
) {
    if (artists.isEmpty()) {
        Text("Desconocido", style = style, color = color, modifier = modifier)
        return
    }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = horizontalArrangement,
        verticalArrangement = Arrangement.Center
    ) {
        artists.forEachIndexed { index, artist ->
            Text(
                text = artist.name,
                style = style,
                color = color,
                modifier = Modifier.clickable { onArtistClick(artist.id) }
            )
            if (index < artists.lastIndex) {
                Text(", ", style = style, color = color)
            }
        }
    }
}
