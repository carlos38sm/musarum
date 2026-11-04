package com.musarum.mobile.profile.version

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import kotlinx.coroutines.launch

/**
 * Pantalla OWNER para publicar un anuncio de actualización del APK.
 *
 * Flujo pensado: pushear un tag "android-vX.Y.Z" desde el PC, esperar a que
 * el workflow verde haga la Release, y aquí pegar la versión → botón
 * "Rellenar URL" → "Publicar". A partir de ese segundo, todos los móviles
 * verán el diálogo al abrir la app.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PublishUpdateScreen(onBack: () -> Unit) {
    val container = LocalAppContainer.current
    val viewModel: PublishUpdateViewModel = viewModel(
        factory = PublishUpdateViewModel.factory(container.apiService)
    )
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Publicar actualización") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Atrás")
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Recargar")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        when (val s = state) {
            is PublishUpdateUiState.Loading -> CenterSpinner(padding)
            is PublishUpdateUiState.Error -> CenterMessage(padding, s.message)
            is PublishUpdateUiState.Ready -> FormContent(
                padding = padding,
                state = s,
                onSubmit = { v, u, m ->
                    viewModel.publish(v, u, m) { ok, msg ->
                        scope.launch { snackbarHostState.showSnackbar(msg) }
                    }
                }
            )
        }
    }
}

@Composable
private fun CenterSpinner(padding: PaddingValues) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun CenterMessage(padding: PaddingValues, message: String) {
    Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
        Text(message, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun FormContent(
    padding: PaddingValues,
    state: PublishUpdateUiState.Ready,
    onSubmit: (version: String, url: String, mandatory: Boolean) -> Unit
) {
    var version by rememberSaveable { mutableStateOf("") }
    var url by rememberSaveable { mutableStateOf("") }
    var mandatory by rememberSaveable { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        CurrentCard(state = state)

        Text(
            text = "Nueva publicación",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp)
        )

        OutlinedTextField(
            value = version,
            onValueChange = { version = it },
            label = { Text("Versión (ej. 1.0.4)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            label = { Text("URL del APK") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedButton(
            onClick = { url = PublishUpdateViewModel.suggestApkUrl(version) },
            enabled = version.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Rellenar URL desde versión")
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Actualización obligatoria", style = MaterialTheme.typography.bodyLarge)
                Text(
                    "Si se activa, el diálogo no se podrá cerrar sin actualizar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(checked = mandatory, onCheckedChange = { mandatory = it })
        }

        Spacer(Modifier.height(4.dp))

        Button(
            onClick = { onSubmit(version, url, mandatory) },
            enabled = !state.submitting && version.isNotBlank() && url.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            if (state.submitting) {
                CircularProgressIndicator(
                    modifier = Modifier.height(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
                Spacer(Modifier.height(0.dp))
                Text("  Publicando...")
            } else {
                Text("Publicar")
            }
        }

        OutlinedButton(
            onClick = { onSubmit("", "", false) },
            enabled = !state.submitting && !state.currentLatestApk.isNullOrBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Retirar anuncio actual")
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun CurrentCard(state: PublishUpdateUiState.Ready) {
    Surface(
        tonalElevation = 2.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "Publicado ahora",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            val v = state.currentLatestApk
            if (v.isNullOrBlank()) {
                Text(
                    "Sin anuncio activo — los móviles no verán ningún diálogo.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                Text("Versión: $v", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                state.currentApkUrl?.takeIf { it.isNotBlank() }?.let {
                    Text("URL: $it", style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    if (state.currentMandatory) "Obligatoria" else "Opcional",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            HorizontalDivider(modifier = Modifier.padding(top = 4.dp))
            Text(
                "Los móviles la detectan al abrir la app (GET /version).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
