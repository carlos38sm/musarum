package com.musarum.mobile.player

import android.graphics.Bitmap
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.ImageLoader
import coil3.request.ImageRequest
import coil3.request.allowHardware
import coil3.toBitmap
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.music.resolveCoverUrl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extrae y recuerda el color dominante de la portada de la canción actual,
 * animando el cambio para que la transición entre canciones no sea un
 * flashazo. Si no hay cover o Palette no encuentra swatch útil, cae al
 * color por defecto (habitualmente el primary del tema).
 *
 * Hace el trabajo pesado (decode + Palette.generate()) en Dispatchers.Default
 * fuera del hilo principal. La imagen se pide a Coil con allowHardware=false
 * para que el bitmap sea legible por Palette (los HARDWARE bitmaps no se
 * pueden samplear en CPU y lanzan IllegalArgumentException).
 */
@Composable
fun rememberDominantCoverColor(
    coverUrl: String?,
    fallback: Color
): State<Color> {
    val context = LocalContext.current
    val baseUrl = LocalAppContainer.current.baseUrl
    var extracted by remember { mutableStateOf(fallback) }

    LaunchedEffect(coverUrl) {
        val resolved = resolveCoverUrl(coverUrl, baseUrl)
        if (resolved.isNullOrBlank()) {
            extracted = fallback
            return@LaunchedEffect
        }
        val color = withContext(Dispatchers.Default) {
            runCatching {
                val loader = ImageLoader(context)
                val request = ImageRequest.Builder(context)
                    .data(resolved)
                    .allowHardware(false)
                    .build()
                val bitmap: Bitmap? = loader.execute(request).image?.toBitmap()
                bitmap?.let { pickDominant(it) }
            }.getOrNull()
        }
        extracted = color ?: fallback
    }

    // Animamos el cambio para no cortar: 450ms es suficiente para notar la
    // transición sin que parezca lenta.
    return animateColorAsState(
        targetValue = extracted,
        animationSpec = tween(durationMillis = 450),
        label = "dominantCoverColor"
    )
}

/**
 * Elige el swatch más representativo del bitmap: preferimos Vibrant por su
 * carácter (ese "naranja" de una portada cálida), con DarkVibrant / Muted
 * como respaldo cuando la imagen es tenue. Si todo falla, devolvemos null
 * y el caller decide el fallback.
 */
private fun pickDominant(bitmap: Bitmap): Color? {
    val palette = Palette.from(bitmap)
        .maximumColorCount(16)
        .generate()
    val argb = palette.vibrantSwatch?.rgb
        ?: palette.darkVibrantSwatch?.rgb
        ?: palette.lightVibrantSwatch?.rgb
        ?: palette.mutedSwatch?.rgb
        ?: palette.dominantSwatch?.rgb
        ?: return null
    return Color(argb)
}

/** Conveniencia por si en el futuro quieres leer el toArgb en tests. */
@Suppress("unused")
internal fun Color.argb() = this.toArgb()
