package com.musarum.mobile.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.musarum.mobile.LocalAppContainer

/** Tipos de placeholder servidos por el backend (ver PlaceholderService). */
enum class PlaceholderKind { SONGS, ALBUMS, ARTISTS, PLAYLISTS }

@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerRadius: Dp = 6.dp,
    placeholder: ImageVector = Icons.Filled.Album,
    // Bloque B18: si la portada real está vacía, el backend puede servir
    // una imagen aleatoria determinista por seed desde _placeholders/.
    // Si estos dos vienen a null o la carpeta está vacía, cae al icono.
    placeholderKind: PlaceholderKind? = null,
    placeholderSeed: Long? = null
) {
    val baseUrl = LocalAppContainer.current.baseUrl
    val resolved = resolveCoverUrl(url, baseUrl)
    val fallbackUrl = remember(placeholderKind, placeholderSeed, baseUrl) {
        if (placeholderKind != null && placeholderSeed != null) {
            "${baseUrl.trimEnd('/')}/placeholders/${placeholderKind.name.lowercase()}/${placeholderSeed}"
        } else null
    }
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        when {
            !resolved.isNullOrBlank() -> AsyncImage(
                model = resolved,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            fallbackUrl != null -> AsyncImage(
                model = fallbackUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                // Si el backend devuelve 404 (carpeta vacía), Coil pinta
                // el icono gris. Lo usamos como "doble fallback" para no
                // dejar el espacio en blanco.
                error = androidx.compose.ui.graphics.painter.ColorPainter(
                    MaterialTheme.colorScheme.surfaceVariant
                )
            )
            else -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = placeholder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun ArtistAvatar(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    placeholderSeed: Long? = null
) = CoverImage(
    url = url,
    modifier = modifier,
    size = size,
    cornerRadius = size / 2,
    placeholder = Icons.Filled.Person,
    placeholderKind = PlaceholderKind.ARTISTS,
    placeholderSeed = placeholderSeed
)

/**
 * Avatar que muestra la inicial del nick en color cuando no hay imagen.
 * Reemplaza al icono gris de Person — más personal, más Netflix.
 * El color se deriva del propio nick de forma determinista (siempre el
 * mismo color para el mismo user). Si hay avatarUrl, usa esa imagen.
 */
@Composable
fun UserAvatar(
    nick: String?,
    avatarUrl: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) {
    val baseUrl = LocalAppContainer.current.baseUrl
    val resolved = resolveCoverUrl(avatarUrl, baseUrl)
    val initial = nick?.trim()?.firstOrNull()?.uppercase() ?: "?"
    val bg = initialColor(nick ?: "")
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(size / 2),
        color = bg
    ) {
        if (resolved.isNullOrBlank()) {
            Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                Text(
                    text = initial,
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    // Escala la fuente al tamaño del avatar.
                    fontSize = (size.value * 0.42f).sp
                )
            }
        } else {
            AsyncImage(
                model = resolved,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Deriva un color HSL estable a partir del nick usando su hashCode como
 * fuente de aleatoriedad. Saturación y luminancia controladas para que
 * siempre se vea bien sobre texto blanco.
 */
private fun initialColor(key: String): Color {
    val hash = key.hashCode()
    // Hue 0..359 derivado del hash; saturación 60%, lightness 48% → pastel
    // vivo con buen contraste contra texto blanco.
    val hue = (hash and 0x1FF) % 360
    val h = hue / 360f
    val s = 0.55f
    val l = 0.48f
    // HSL → RGB
    val c = (1 - kotlin.math.abs(2 * l - 1)) * s
    val x = c * (1 - kotlin.math.abs((h * 6 % 2) - 1))
    val m = l - c / 2
    val (r, g, b) = when {
        h < 1f/6 -> Triple(c, x, 0f)
        h < 2f/6 -> Triple(x, c, 0f)
        h < 3f/6 -> Triple(0f, c, x)
        h < 4f/6 -> Triple(0f, x, c)
        h < 5f/6 -> Triple(x, 0f, c)
        else     -> Triple(c, 0f, x)
    }
    return Color(r + m, g + m, b + m, 1f)
}

/**
 * Resuelve una URL de portada:
 *   - null / vacío → null (muestra placeholder)
 *   - URL absoluta ("http...") → se usa tal cual (ej. CoverArtArchive)
 *   - URL relativa ("/albums/1/cover") → prepone el baseUrl del backend
 */
fun resolveCoverUrl(url: String?, baseUrl: String): String? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    val base = baseUrl.trimEnd('/')
    val path = url.trimStart('/')
    return "$base/$path"
}

/**
 * Mosaico 2×2 de covers para playlists (estilo Spotify). Siempre dibuja
 * 4 celdas; los huecos (menos de 4 canciones o canciones sin cover) se
 * rellenan con placeholder de playlist. Si [urls] va vacío, las 4 celdas
 * son todas placeholder — queda una "cuadrícula" uniforme en vez de un
 * cuadro gris plano.
 */
@Composable
fun PlaylistMosaicCover(
    urls: List<String?>,
    playlistId: Long,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    cornerRadius: Dp = 10.dp
) {
    val padded = (urls + List(4) { null }).take(4)
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize()
        ) {
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                MosaicCell(
                    url = padded[0], playlistId = playlistId, slot = 0,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
                MosaicCell(
                    url = padded[1], playlistId = playlistId, slot = 1,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            }
            androidx.compose.foundation.layout.Row(
                modifier = Modifier.weight(1f).fillMaxWidth()
            ) {
                MosaicCell(
                    url = padded[2], playlistId = playlistId, slot = 2,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
                MosaicCell(
                    url = padded[3], playlistId = playlistId, slot = 3,
                    modifier = Modifier.weight(1f).fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun MosaicCell(
    url: String?,
    playlistId: Long,
    slot: Int,
    modifier: Modifier
) {
    val baseUrl = LocalAppContainer.current.baseUrl
    val resolved = resolveCoverUrl(url, baseUrl)
    // Seed derivada de (playlist, slot) para que cada hueco escoja una
    // imagen distinta pero estable entre recargas.
    val seed = playlistId * 4L + slot
    val fallbackUrl = remember(baseUrl, seed) {
        "${baseUrl.trimEnd('/')}/placeholders/playlists/${seed}"
    }
    Box(modifier = modifier) {
        AsyncImage(
            model = resolved ?: fallbackUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
            error = androidx.compose.ui.graphics.painter.ColorPainter(
                MaterialTheme.colorScheme.surfaceVariant
            )
        )
    }
}
