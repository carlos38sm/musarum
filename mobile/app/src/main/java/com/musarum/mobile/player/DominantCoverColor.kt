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
import coil3.SingletonImageLoader
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
                // Reutilizamos el SingletonImageLoader que MusarumApplication
                // configura con el OkHttpClient autenticado del AppContainer.
                // Antes construíamos un ImageLoader "pelado" aquí y las covers
                // del backend (/albums/{id}/cover, que exigen JWT) fallaban
                // con 401 → el fondo del NowPlaying se quedaba siempre en el
                // fallback en vez de tomar el color real de la portada.
                val loader = SingletonImageLoader.get(context)
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

/**
 * Par de colores de la portada: el dominante (primario) y un secundario
 * suficientemente distinto. Para el MiniPlayer queremos intercalar entre
 * fondo, botón play y anillo de progreso — usar 3 tonos del mismo color
 * mata el contraste. Si no hay portada o Palette no da suficiente variedad,
 * se degrada a un par fallback que sigue manteniendo diferencia de tono.
 */
data class CoverColorPair(val primary: Color, val secondary: Color)

@Composable
fun rememberCoverColorPair(
    coverUrl: String?,
    fallbackPrimary: Color,
    fallbackSecondary: Color
): State<CoverColorPair> {
    val context = LocalContext.current
    val baseUrl = LocalAppContainer.current.baseUrl
    var extracted by remember {
        mutableStateOf(CoverColorPair(fallbackPrimary, fallbackSecondary))
    }

    LaunchedEffect(coverUrl) {
        val resolved = resolveCoverUrl(coverUrl, baseUrl)
        if (resolved.isNullOrBlank()) {
            extracted = CoverColorPair(fallbackPrimary, fallbackSecondary)
            return@LaunchedEffect
        }
        val pair = withContext(Dispatchers.Default) {
            runCatching {
                val loader = SingletonImageLoader.get(context)
                val request = ImageRequest.Builder(context)
                    .data(resolved)
                    .allowHardware(false)
                    .build()
                val bitmap: Bitmap? = loader.execute(request).image?.toBitmap()
                bitmap?.let { pickPair(it) }
            }.getOrNull()
        }
        extracted = pair ?: CoverColorPair(fallbackPrimary, fallbackSecondary)
    }

    val animatedPrimary by animateColorAsState(
        targetValue = extracted.primary,
        animationSpec = tween(durationMillis = 450),
        label = "coverPairPrimary"
    )
    val animatedSecondary by animateColorAsState(
        targetValue = extracted.secondary,
        animationSpec = tween(durationMillis = 450),
        label = "coverPairSecondary"
    )
    return remember(animatedPrimary, animatedSecondary) {
        androidx.compose.runtime.derivedStateOf {
            CoverColorPair(animatedPrimary, animatedSecondary)
        }
    }
}

/**
 * Elige dos swatches distintos de la portada. El primero: el Vibrant
 * habitual. El segundo: el que más contraste de tono (hue) tenga con el
 * primero de entre los otros swatches disponibles. Así en una portada
 * "rojiza" el segundo tiende a ser el verde/amarillo apagado del fondo o
 * similar, no otro rojo casi idéntico.
 */
private fun pickPair(bitmap: Bitmap): CoverColorPair? {
    val palette = Palette.from(bitmap)
        .maximumColorCount(24)
        .generate()
    val primarySwatch = palette.vibrantSwatch
        ?: palette.darkVibrantSwatch
        ?: palette.lightVibrantSwatch
        ?: palette.mutedSwatch
        ?: palette.dominantSwatch
        ?: return null
    val candidates = listOfNotNull(
        palette.lightVibrantSwatch,
        palette.darkVibrantSwatch,
        palette.vibrantSwatch,
        palette.lightMutedSwatch,
        palette.darkMutedSwatch,
        palette.mutedSwatch,
        palette.dominantSwatch
    ).filter { it !== primarySwatch }

    val primaryHue = primarySwatch.hsl[0]
    val secondarySwatch = candidates.maxByOrNull { sw ->
        val hueDiff = kotlin.math.abs(sw.hsl[0] - primaryHue).let {
            if (it > 180f) 360f - it else it
        }
        hueDiff
    } ?: primarySwatch
    return CoverColorPair(Color(primarySwatch.rgb), Color(secondarySwatch.rgb))
}

/** Conveniencia por si en el futuro quieres leer el toArgb en tests. */
@Suppress("unused")
internal fun Color.argb() = this.toArgb()
