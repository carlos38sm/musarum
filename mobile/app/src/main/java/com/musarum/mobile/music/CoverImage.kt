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
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.musarum.mobile.BuildConfig

@Composable
fun CoverImage(
    url: String?,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerRadius: Dp = 6.dp,
    placeholder: ImageVector = Icons.Filled.Album
) {
    val resolved = resolveCoverUrl(url)
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
 * Resuelve una URL de portada:
 *   - null / vacío → null (muestra placeholder)
 *   - URL absoluta ("http...") → se usa tal cual (ej. CoverArtArchive)
 *   - URL relativa ("/albums/1/cover") → prepone el baseUrl del backend
 */
fun resolveCoverUrl(url: String?): String? {
    if (url.isNullOrBlank()) return null
    if (url.startsWith("http://") || url.startsWith("https://")) return url
    val base = BuildConfig.MUSARUM_BASE_URL.trimEnd('/')
    val path = url.trimStart('/')
    return "$base/$path"
}
