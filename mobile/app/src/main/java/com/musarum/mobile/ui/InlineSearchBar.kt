package com.musarum.mobile.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp

/**
 * Icono de lupa pensado para ir en `actions` de un `TopAppBar`. Alterna un
 * flag externo — el composable no guarda estado propio para que la pantalla
 * pueda cerrar la b&uacute;squeda desde fuera (p. ej. al volver atr&aacute;s).
 */
@Composable
fun SearchActionIcon(active: Boolean, onToggle: () -> Unit) {
    IconButton(onClick = onToggle) {
        Icon(
            imageVector = if (active) Icons.Filled.Close else Icons.Filled.Search,
            contentDescription = if (active) "Cerrar búsqueda" else "Buscar"
        )
    }
}

/**
 * Barra de b&uacute;squeda inline que se despliega bajo la TopAppBar. Pensada
 * para filtrar listas largas (playlist, &aacute;lbum, artista) sin afear la
 * cabecera con una barra siempre visible.
 *
 * - `visible=true`  → se expande verticalmente, pide foco y aparece teclado.
 * - `visible=false` → colapsa; el caller decide si limpiar la query.
 *
 * El composable NO filtra ni mantiene estado propio: la pantalla pasa
 * `query` + `onQueryChange` y recalcula la lista con `remember(query)`.
 */
@Composable
fun InlineSearchBar(
    visible: Boolean,
    query: String,
    onQueryChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    // Al mostrarse, un pequeño delay evita condiciones con la animación de
    // expand — sin esto, request() antes de estar en composición final llega
    // a no pedir foco en algunos equipos.
    LaunchedEffect(visible) {
        if (visible) {
            kotlinx.coroutines.delay(60)
            runCatching { focusRequester.requestFocus() }
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = expandVertically() + fadeIn(),
        exit = shrinkVertically() + fadeOut(),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text(placeholder) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                Icons.Filled.Close,
                                contentDescription = "Limpiar",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
            )
            Spacer(Modifier.width(0.dp))
        }
    }
}
