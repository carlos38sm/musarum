package com.musarum.mobile.music

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Cabecera uniforme para pantallas-lista. Ahora en horizontal, con cada
 * métrica como par (valor en primary · etiqueta en onSurfaceVariant) y una
 * separación tenue entre ellas para que no compitan con el título de la
 * pantalla ni con las filas de contenido.
 *
 * Compat: el API viejo con primary/secondary strings sigue funcionando y
 * los parte en (número, resto) con un split simple.
 */
@Composable
fun LibraryStatsHeader(
    primary: String,
    secondary: String? = null,
    modifier: Modifier = Modifier
) {
    val cells = buildList {
        splitValueLabel(primary)?.let { add(it) }
        secondary?.let { splitValueLabel(it)?.let { pair -> add(pair) } }
    }
    LibraryStatsHeaderInternal(cells, modifier)
}

/**
 * Variante con celdas explícitas para pantallas que ya saben el reparto.
 */
@Composable
fun LibraryStatsHeader(
    cells: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) = LibraryStatsHeaderInternal(cells, modifier)

@Composable
private fun LibraryStatsHeaderInternal(
    cells: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    if (cells.isEmpty()) return
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        cells.forEachIndexed { index, (value, label) ->
            if (index > 0) {
                // Barra fina ámbar entre métricas.
                Spacer(Modifier.width(14.dp))
                Box(
                    modifier = Modifier
                        .width(1.dp)
                        .height(28.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
                )
                Spacer(Modifier.width(14.dp))
            }
            Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                Text(
                    value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * Divide "347 canciones" o "4 h 23 min" en (valor, etiqueta). Si no cuadra,
 * devuelve null → esa celda no se renderiza en vez de romper el layout.
 */
private fun splitValueLabel(text: String): Pair<String, String>? {
    val trimmed = text.trim()
    if (trimmed.isEmpty()) return null
    val tokens = trimmed.split(' ').filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return null
    // Caso 1: "347 canciones" → ("347", "canciones")
    // Caso 2: "4 h 23 min" → ("4 h 23 min", "duración") agrupando los dígitos.
    val letterStart = tokens.indexOfFirst { it.all { ch -> ch.isLetter() } }
    return when {
        tokens.size == 2 && tokens[0].all { it.isDigit() } ->
            tokens[0] to tokens[1]
        letterStart <= 0 ->
            trimmed to ""
        else -> {
            val value = tokens.take(letterStart + 1).joinToString(" ")
            val label = tokens.drop(letterStart + 1).joinToString(" ").ifBlank { "en total" }
            value to label
        }
    }
}

