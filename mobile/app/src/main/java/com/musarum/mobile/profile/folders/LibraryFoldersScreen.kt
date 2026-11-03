package com.musarum.mobile.profile.folders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.LocalSnackbar
import com.musarum.mobile.data.network.dto.DiscoveredFolderDto
import com.musarum.mobile.data.network.dto.LibraryFolderDto

/**
 * Gestión de sub-carpetas de biblioteca — solo accesible al OWNER (el
 * backend bloquea 403 a cualquier otro). La pantalla ofrece: lista de
 * carpetas registradas con switch de activar/desactivar y botón de borrar,
 * y una fila "Añadir" (FAB) que abre un diálogo con las sub-carpetas que
 * existen físicamente bajo la paraguas pero aún no están registradas.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryFoldersScreen(
    onBack: () -> Unit,
    viewModel: LibraryFoldersViewModel = viewModel(
        factory = LibraryFoldersViewModel.factory(
            LocalAppContainer.current.libraryFolderRepository
        )
    )
) {
    val state by viewModel.state.collectAsState()
    val snackbar = LocalSnackbar.current
    var showAdd by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Carpetas de música") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Volver"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAdd = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = androidx.compose.foundation.shape.CircleShape
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Añadir carpeta")
            }
        }
    ) { padding ->
        when {
            state.loading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) { CircularProgressIndicator() }

            state.error != null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    state.error ?: "",
                    color = MaterialTheme.colorScheme.error
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(bottom = 96.dp)
            ) {
                item { HeaderExplainer() }
                items(state.folders, key = { "f_${it.id}" }) { folder ->
                    FolderRow(
                        folder = folder,
                        onToggle = { active ->
                            viewModel.toggle(folder.id, active) { _, msg -> snackbar(msg) }
                        },
                        onDelete = {
                            viewModel.delete(folder.id) { _, msg -> snackbar(msg) }
                        }
                    )
                    HorizontalDivider()
                }
                if (state.folders.isEmpty()) {
                    item {
                        Text(
                            "No hay carpetas registradas aún. Añade una con el +.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddFolderDialog(
            discoverable = state.discoverable,
            onDismiss = { showAdd = false },
            onPickDiscovered = { d ->
                viewModel.add(d.path) { _, msg -> snackbar(msg) }
                showAdd = false
            },
            onAddCustom = { path ->
                viewModel.add(path) { _, msg -> snackbar(msg) }
                showAdd = false
            }
        )
    }
}

@Composable
private fun HeaderExplainer() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)
    ) {
        Text(
            "Musarum escanea solo las carpetas marcadas aquí. " +
                "Para añadir una, debe vivir dentro de la carpeta raíz que " +
                "montaste en el servidor.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun FolderRow(
    folder: LibraryFolderDto,
    onToggle: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (folder.existsOnDisk) Icons.Filled.Folder else Icons.Filled.FolderOff,
            contentDescription = null,
            tint = if (folder.existsOnDisk) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.error
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                folder.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                buildString {
                    append(folder.path)
                    if (!folder.existsOnDisk) append(" · no existe en disco")
                    else if (folder.songCount > 0) append(" · ${folder.songCount} canciones")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Switch(
            checked = folder.active,
            onCheckedChange = onToggle,
            enabled = folder.existsOnDisk
        )
        IconButton(onClick = onDelete) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = "Eliminar",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun AddFolderDialog(
    discoverable: List<DiscoveredFolderDto>,
    onDismiss: () -> Unit,
    onPickDiscovered: (DiscoveredFolderDto) -> Unit,
    onAddCustom: (String) -> Unit
) {
    var customPath by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Añadir carpeta") },
        text = {
            Column {
                if (discoverable.isEmpty()) {
                    Text(
                        "No hay sub-carpetas sin registrar bajo la carpeta " +
                            "raíz. Si creaste una ahora, pulsa recargar " +
                            "en la pantalla anterior.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Sub-carpetas detectadas:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyColumn(
                        modifier = Modifier.height(220.dp).fillMaxWidth()
                    ) {
                        items(discoverable, key = { "d_${it.path}" }) { d ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Filled.Folder,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(d.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(
                                        d.path,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                TextButton(onClick = { onPickDiscovered(d) }) {
                                    Text("Añadir")
                                }
                            }
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "O ruta manual (debe estar bajo la raíz):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = customPath,
                    onValueChange = { customPath = it },
                    singleLine = true,
                    placeholder = { Text("/music/jazz") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = customPath.isNotBlank(),
                onClick = { onAddCustom(customPath.trim()) }
            ) { Text("Añadir manual") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cerrar") }
        }
    )
}
