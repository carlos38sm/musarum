package com.musarum.mobile.profile.users

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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import com.musarum.mobile.data.network.dto.UserResponseDto
import com.musarum.mobile.music.ArtistAvatar

/**
 * Pantalla de administración de usuarios. Solo visible para OWNER (el acceso
 * desde Perfil es condicional, el backend también 403 si lo intenta un
 * MEMBER). Lista: avatar + nick + rol + fila de acciones (switch bloquear,
 * papelera eliminar con confirmación).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsersScreen(
    onBack: () -> Unit,
    viewModel: UsersViewModel = viewModel(
        factory = UsersViewModel.factory(LocalAppContainer.current.userRepository)
    )
) {
    val state by viewModel.state.collectAsState()
    val snackbar = LocalSnackbar.current
    var confirmDelete by remember { mutableStateOf<UserResponseDto?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Usuarios") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
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
            ) { Text(state.error ?: "", color = MaterialTheme.colorScheme.error) }

            else -> LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(state.users, key = { it.id }) { user ->
                    UserRow(
                        user = user,
                        onToggleBlocked = { blocked ->
                            viewModel.toggleBlocked(user.id, blocked) { _, msg -> snackbar(msg) }
                        },
                        onDelete = { confirmDelete = user }
                    )
                    HorizontalDivider()
                }
                if (state.users.isEmpty()) {
                    item {
                        Text(
                            "No hay usuarios registrados.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            }
        }
    }

    confirmDelete?.let { user ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            title = { Text("¿Eliminar \"${user.nick}\"?") },
            text = {
                Text(
                    "Se borrarán sus favoritos, entradas en playlists y " +
                        "reproducciones. Sus playlists propias quedarán " +
                        "huérfanas. No se puede deshacer."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(user.id) { _, msg -> snackbar(msg) }
                    confirmDelete = null
                }) { Text("Eliminar", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
private fun UserRow(
    user: UserResponseDto,
    onToggleBlocked: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ArtistAvatar(url = user.avatarUrl, size = 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    user.nick,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (user.blocked) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        Icons.Filled.Block,
                        contentDescription = "Bloqueado",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
            Text(
                when (user.userRole) {
                    "OWNER" -> "Propietario"
                    "MEMBER" -> "Oyente"
                    else -> user.userRole ?: "Oyente"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = !user.blocked,
            onCheckedChange = { active -> onToggleBlocked(!active) }
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
