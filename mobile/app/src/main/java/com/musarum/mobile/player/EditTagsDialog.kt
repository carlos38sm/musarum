package com.musarum.mobile.player

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import com.musarum.mobile.data.network.dto.SongDetailsDto
import com.musarum.mobile.data.network.dto.TagEditRequest

/**
 * Di&aacute;logo para editar tags ID3 + metadatos JPA de una canci&oacute;n. Rellena
 * cada campo con el valor actual; al guardar s&oacute;lo env&iacute;a los que hayan
 * cambiado (patch parcial). El switch "Renombrar archivo" pide al backend
 * mover el fichero a "{artist} - {title}.{ext}" en el mismo directorio.
 */
@Composable
fun EditTagsDialog(
    details: SongDetailsDto,
    onDismiss: () -> Unit,
    onSave: (TagEditRequest) -> Unit
) {
    val initialArtists = details.artists.joinToString(", ") { it.name }
    var title by remember { mutableStateOf(details.title) }
    var artist by remember { mutableStateOf(initialArtists) }
    var album by remember { mutableStateOf(details.albumTitle ?: "") }
    var genre by remember { mutableStateOf(details.genreName ?: "") }
    var year by remember { mutableStateOf(details.year?.toString() ?: "") }
    var trackNumber by remember { mutableStateOf(details.trackNumber?.toString() ?: "") }
    var renameFile by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Editar tags",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Título") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = artist,
                    onValueChange = { artist = it },
                    label = { Text("Artista(s) · separa con coma") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = album,
                    onValueChange = { album = it },
                    label = { Text("Álbum") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = genre,
                    onValueChange = { genre = it },
                    label = { Text("Género") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth()) {
                    OutlinedTextField(
                        value = year,
                        onValueChange = { year = it.filter { c -> c.isDigit() }.take(4) },
                        label = { Text("Año") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    OutlinedTextField(
                        value = trackNumber,
                        onValueChange = { trackNumber = it.filter { c -> c.isDigit() }.take(3) },
                        label = { Text("Pista") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Renombrar archivo",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            "“${sanitize(artist.substringBefore(","))} - ${sanitize(title)}.ext”",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(checked = renameFile, onCheckedChange = { renameFile = it })
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                // Patch parcial: sólo enviamos los campos que cambiaron o que
                // el usuario borró a propósito (p. ej. vaciar el género).
                // Si el campo está vacío y era null originalmente, no mandamos.
                val req = TagEditRequest(
                    title = title.takeIf { it != details.title },
                    artist = artist.takeIf { it != initialArtists },
                    album = album.takeIf { it != (details.albumTitle ?: "") },
                    genre = genre.takeIf { it != (details.genreName ?: "") },
                    year = year.toIntOrNull()?.takeIf { it != details.year },
                    trackNumber = trackNumber.toIntOrNull()?.takeIf { it != details.trackNumber },
                    renameFile = renameFile.takeIf { it }
                )
                onSave(req)
            }) { Text("Guardar") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

private fun sanitize(s: String): String =
    s.trim().replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "…" }
