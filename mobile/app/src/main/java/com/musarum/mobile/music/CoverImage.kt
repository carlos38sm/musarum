package com.musarum.mobile.music

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
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

@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerRadius: Dp = 6.dp,
    placeholder: ImageVector = Icons.Filled.Album
) {
    val baseUrl = LocalAppContainer.current.baseUrl
    val resolved = resolveCoverUrl(url, baseUrl)
    Surface(
        modifier = modifier.size(size),
        shape = RoundedCornerShape(cornerRadius),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        if (resolved.isNullOrBlank()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = placeholder,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
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

@Composable
fun ArtistAvatar(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp
) = CoverImage(
    url = url,
    modifier = modifier,
    size = size,
    cornerRadius = size / 2,
    placeholder = Icons.Filled.Person
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
