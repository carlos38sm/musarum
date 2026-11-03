package com.musarum.mobile.music

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Agriculture
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Brightness5
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Forest
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Hardware
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Spa
import androidx.compose.material.icons.filled.Speaker
import androidx.compose.material.icons.filled.Stars
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material.icons.filled.Whatshot
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Visual asociado a un género: icono + gradiente (dos colores) para pintar
 * tiles cuando no hay portada. Ported del Musarum viejo.
 */
data class GenreVisual(
    val icon: ImageVector,
    val colorTop: Color,
    val colorBottom: Color
)

private val DEFAULT_GENRE_VISUALS = listOf(
    GenreVisual(Icons.Filled.MusicNote,    Color(0xFFE91E63), Color(0xFF880E4F)),
    GenreVisual(Icons.Filled.LibraryMusic, Color(0xFF3F51B5), Color(0xFF1A237E)),
    GenreVisual(Icons.Filled.Album,        Color(0xFF4CAF50), Color(0xFF1B5E20)),
    GenreVisual(Icons.Filled.Headphones,   Color(0xFFFF9800), Color(0xFFE65100)),
    GenreVisual(Icons.Filled.Mic,          Color(0xFF9C27B0), Color(0xFF4A148C)),
    GenreVisual(Icons.Filled.Piano,        Color(0xFF00BCD4), Color(0xFF006064)),
    GenreVisual(Icons.Filled.GraphicEq,    Color(0xFFFFC107), Color(0xFFFF6F00)),
    GenreVisual(Icons.Filled.Radio,        Color(0xFFF44336), Color(0xFFB71C1C))
)

private val EXPLICIT_GENRES: Map<String, GenreVisual> = mapOf(
    "pop" to GenreVisual(Icons.Filled.Stars,                   Color(0xFFFF6B9D), Color(0xFFC44569)),
    "rock" to GenreVisual(Icons.Filled.LocalFireDepartment,    Color(0xFFE53935), Color(0xFF8B0000)),
    "rap/hip hop" to GenreVisual(Icons.Filled.Mic,             Color(0xFF424242), Color(0xFF000000)),
    "rap" to GenreVisual(Icons.Filled.Mic,                     Color(0xFF424242), Color(0xFF000000)),
    "hip hop" to GenreVisual(Icons.Filled.Mic,                 Color(0xFF424242), Color(0xFF000000)),
    "electro" to GenreVisual(Icons.Filled.GraphicEq,           Color(0xFF00E5FF), Color(0xFF0091EA)),
    "electronic" to GenreVisual(Icons.Filled.GraphicEq,        Color(0xFF00E5FF), Color(0xFF0091EA)),
    "dance" to GenreVisual(Icons.Filled.Bolt,                  Color(0xFFFFEB3B), Color(0xFFF57F17)),
    "flamenco" to GenreVisual(Icons.Filled.LocalFireDepartment, Color(0xFFFF5722), Color(0xFFBF360C)),
    "alternativo" to GenreVisual(Icons.Filled.Brightness5,     Color(0xFF7B1FA2), Color(0xFF311B92)),
    "alternative" to GenreVisual(Icons.Filled.Brightness5,     Color(0xFF7B1FA2), Color(0xFF311B92)),
    "jazz" to GenreVisual(Icons.Filled.Piano,                  Color(0xFFD4A574), Color(0xFF6D4C00)),
    "blues" to GenreVisual(Icons.Filled.Piano,                 Color(0xFF1976D2), Color(0xFF0D47A1)),
    "classical" to GenreVisual(Icons.Filled.LibraryMusic,      Color(0xFF8D6E63), Color(0xFF3E2723)),
    "clásica" to GenreVisual(Icons.Filled.LibraryMusic,        Color(0xFF8D6E63), Color(0xFF3E2723)),
    "reggaeton" to GenreVisual(Icons.Filled.Whatshot,          Color(0xFF00C853), Color(0xFF1B5E20)),
    "latin" to GenreVisual(Icons.Filled.WbSunny,               Color(0xFFFFA726), Color(0xFFE65100)),
    "latino" to GenreVisual(Icons.Filled.WbSunny,              Color(0xFFFFA726), Color(0xFFE65100)),
    "indie" to GenreVisual(Icons.Filled.Forest,                Color(0xFF66BB6A), Color(0xFF2E7D32)),
    "metal" to GenreVisual(Icons.Filled.Hardware,              Color(0xFF455A64), Color(0xFF1C2833)),
    "punk" to GenreVisual(Icons.Filled.FlashOn,                Color(0xFFE91E63), Color(0xFFAD1457)),
    "country" to GenreVisual(Icons.Filled.Agriculture,         Color(0xFFA1887F), Color(0xFF4E342E)),
    "folk" to GenreVisual(Icons.Filled.Forest,                 Color(0xFF8BC34A), Color(0xFF558B2F)),
    "soul" to GenreVisual(Icons.Filled.Favorite,               Color(0xFFAB47BC), Color(0xFF6A1B9A)),
    "rnb" to GenreVisual(Icons.Filled.Favorite,                Color(0xFFAB47BC), Color(0xFF6A1B9A)),
    "r&b" to GenreVisual(Icons.Filled.Favorite,                Color(0xFFAB47BC), Color(0xFF6A1B9A)),
    "funk" to GenreVisual(Icons.Filled.AutoAwesome,            Color(0xFFFF6F00), Color(0xFFE65100)),
    "disco" to GenreVisual(Icons.Filled.AutoAwesome,           Color(0xFFFFD700), Color(0xFFFF8F00)),
    "techno" to GenreVisual(Icons.Filled.Memory,               Color(0xFF00E5FF), Color(0xFF006064)),
    "house" to GenreVisual(Icons.Filled.Speaker,               Color(0xFF03A9F4), Color(0xFF01579B)),
    "ambient" to GenreVisual(Icons.Filled.Spa,                 Color(0xFF80CBC4), Color(0xFF00695C)),
    "gospel" to GenreVisual(Icons.Filled.LightMode,            Color(0xFFFFD54F), Color(0xFFFF8F00)),
    "world" to GenreVisual(Icons.Filled.Public,                Color(0xFF26A69A), Color(0xFF004D40))
)

/**
 * Determinístico: match explícito > fallback por hash del nombre. Siempre
 * devuelve algo, nunca null — así podemos pintar el género aunque no esté
 * en el mapa curado.
 */
fun getGenreVisual(genreName: String): GenreVisual {
    val key = genreName.lowercase().trim()
    EXPLICIT_GENRES[key]?.let { return it }
    val size = DEFAULT_GENRE_VISUALS.size
    val index = ((key.hashCode() % size) + size) % size
    return DEFAULT_GENRE_VISUALS[index]
}
