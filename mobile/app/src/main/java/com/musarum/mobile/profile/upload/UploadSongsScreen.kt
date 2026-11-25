package com.musarum.mobile.profile.upload

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar

// Pantalla OWNER para subir audios al servidor desde el mÃ³vil. Usa el
// Storage Access Framework de Android (OpenMultipleDocuments) filtrando
// por mime audio. El VM sube cada fichero secuencialmente con POST
// /admin/songs/upload; cada item de la lista refleja su estado en vivo
// (pendiente, subiendo, hecho, error).
//
// El fichero aterriza en MUSARUM_MUSIC_PATH/_inbox/ del servidor y se
// importa en caliente. Emendatium (cuando estÃ© desplegado) renombrarÃ¡ y
// moverÃ¡ a la carpeta canÃ³nica.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UploadSongsScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val container = LocalAppContainer.current
    val snackbar = LocalSnackbar.current
    val viewModel: UploadSongsViewModel = viewModel(
        factory = UploadSongsViewModel.factory(context, container)
    )
    val items by viewModel.items.collectAsStateWithLifecycle()
    val uploading by viewModel.uploading.collectAsStateWithLifecycle()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        val picked = uris.map { uri -> probeUri(context, uri) }
        viewModel.addFiles(picked)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { msg -> snackbar(msg) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Subir canciones") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "AtrÃ¡s")
                    }
                }
            )
        }
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Botonera: seleccionar + subir todas. La selecciÃ³n abre
            // el picker nativo de Android (SAF) filtrado por audio.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Button(
                    onClick = { launcher.launch(arrayOf("audio/*")) },
                    enabled = !uploading
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Elegir audios")
                }
                Button(
                    onClick = { viewModel.startUpload() },
                    enabled = !uploading && items.any { it.status == UploadStatus.Pending }
                ) {
                    Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("Subir ${items.count { it.status == UploadStatus.Pending }}")
                }
            }
            Text(
                "Se envÃ­an al servidor y Emendatium las moverÃ¡ a su " +
                    "carpeta cuando las identifique.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp)
            )
            HorizontalDivider()

            if (items.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.MusicNote,
                            contentDescription = null,
                            modifier = Modifier.size(56.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            "Toca \"Elegir audios\" para empezar",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(items, key = { it.id }) { it ->
                        UploadRow(it)
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

@Composable
private fun UploadRow(item: UploadItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        StatusIcon(item.status)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val sub = buildString {
                if (item.sizeBytes > 0) append(humanBytes(item.sizeBytes))
                item.message?.let {
                    if (isNotEmpty()) append(" Â· ")
                    append(it)
                }
            }
            if (sub.isNotBlank()) {
                Text(
                    sub,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun StatusIcon(status: UploadStatus) {
    when (status) {
        UploadStatus.Pending -> Icon(
            Icons.Filled.HourglassEmpty,
            contentDescription = "Pendiente",
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        UploadStatus.Uploading -> CircularProgressIndicator(
            modifier = Modifier.size(22.dp),
            strokeWidth = 2.dp,
            color = MaterialTheme.colorScheme.primary
        )
        UploadStatus.Done -> Icon(
            Icons.Filled.CheckCircle,
            contentDescription = "Subida",
            tint = MaterialTheme.colorScheme.primary
        )
        UploadStatus.Error -> Icon(
            Icons.Filled.Error,
            contentDescription = "Error",
            tint = MaterialTheme.colorScheme.error
        )
    }
}

/**
 * Obtiene nombre visible y tamaÃ±o del content-URI. En Android ambos
 * se consultan vÃ­a OpenableColumns sobre el ContentResolver. Si el
 * provider del picker no los expone (raro pero posible), caemos a
 * "sin nombre" y tamaÃ±o 0 â€” la subida aÃºn se intenta.
 */
private fun probeUri(context: android.content.Context, uri: Uri): PickedFile {
    var name: String? = null
    var size: Long = 0
    runCatching {
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                val nameIdx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIdx >= 0) name = c.getString(nameIdx)
                val sizeIdx = c.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIdx >= 0) size = c.getLong(sizeIdx)
            }
        }
    }
    return PickedFile(uri, name ?: uri.lastPathSegment ?: "sin nombre", size)
}

/** IdÃ©ntico al humanBytes del panel servidor pero duplicado para no
 *  arrastrar la dependencia al paquete profile.upload. 3-4 lÃ­neas. */
private fun humanBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return "%.1f KB".format(kb)
    val mb = kb / 1024.0
    return if (mb < 1024) "%.1f MB".format(mb) else "%.2f GB".format(mb / 1024.0)
}

data class PickedFile(val uri: Uri, val displayName: String, val sizeBytes: Long)
