package com.musarum.mobile.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.musarum.mobile.LocalAppContainer
import com.musarum.mobile.data.BaseUrlStore
import com.musarum.mobile.data.network.dto.LoginUserDto
import com.musarum.mobile.music.ArtistAvatar

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    viewModel: LoginViewModel = viewModel(
        factory = LoginViewModel.factory(LocalAppContainer.current.authRepository)
    )
) {
    val container = LocalAppContainer.current
    var nick by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var showServerDialog by remember { mutableStateOf(false) }
    val state by viewModel.state.collectAsState()
    val passwordFocus = remember { FocusRequester() }

    // Lista de usuarios (picker estilo Netflix). Se pide una sola vez al
    // abrir la pantalla; si falla (backend caído, URL mal, red), simplemente
    // no se muestra la fila — el form clásico sigue siendo funcional.
    var loginUsers by remember { mutableStateOf<List<LoginUserDto>>(emptyList()) }
    LaunchedEffect(container.baseUrl) {
        runCatching { container.apiService.getLoginUsers() }
            .onSuccess { loginUsers = it }
    }

    LaunchedEffect(state) {
        if (state is LoginUiState.Success) {
            onLoginSuccess()
            viewModel.resetState()
        }
    }

    val primary = MaterialTheme.colorScheme.primary
    val background = MaterialTheme.colorScheme.background

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to primary.copy(alpha = 0.18f),
                    0.4f to background,
                    1.0f to background
                )
            )
    ) {
        // Icono "⚙ servidor" arriba a la derecha. Útil cuando la IP Tailscale
        // del backend cambia y el user quiere entrar antes de ningún login.
        IconButton(
            onClick = { showServerDialog = true },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(8.dp)
        ) {
            Icon(
                Icons.Filled.Dns,
                contentDescription = "Configurar servidor",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // verticalScroll para que en pantallas pequeñas / con el teclado
        // abierto el usuario pueda llegar al botón "Entrar" aunque el
        // contenido no quepa de golpe. Antes, al añadir la fila de bolitas
        // estilo Netflix, el conjunto superaba el alto útil y el botón
        // quedaba cortado por debajo del IME.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Logo con círculo oro ámbar como foto 5
            Surface(
                modifier = Modifier.size(88.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(
                    1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.4f)
                )
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.LibraryMusic,
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Musarum",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "Tu música, en tu propio servidor",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(40.dp))

            // Picker estilo Netflix: si el backend devuelve usuarios, los
            // pintamos como bolitas. Tapear uno rellena el nick y mueve el
            // foco al campo de contraseña. Si la red falla, loginUsers
            // queda vacío y simplemente no mostramos esta fila.
            if (loginUsers.isNotEmpty()) {
                UserPickerRow(
                    users = loginUsers,
                    selectedNick = nick,
                    onSelect = { picked ->
                        nick = picked.nick
                        passwordFocus.requestFocus()
                    }
                )
                Spacer(Modifier.height(24.dp))
            }

            OutlinedTextField(
                value = nick,
                onValueChange = { nick = it },
                label = { Text("Usuario") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = if (passwordVisible) VisualTransformation.None
                else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedLabelColor = MaterialTheme.colorScheme.primary
                ),
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(
                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff
                            else Icons.Filled.Visibility,
                            contentDescription = if (passwordVisible) "Ocultar contraseña"
                            else "Mostrar contraseña"
                        )
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(passwordFocus)
            )

            Spacer(Modifier.height(32.dp))

            when (val current = state) {
                is LoginUiState.Loading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary
                )
                else -> {
                    Button(
                        onClick = { viewModel.login(nick, password) },
                        enabled = nick.isNotBlank() && password.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                    ) {
                        Text(
                            "Entrar",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    if (current is LoginUiState.Error) {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = current.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        }

        if (showServerDialog) {
            ServerUrlDialog(
                current = container.baseUrlStore.current(),
                default = container.baseUrlStore.defaultUrl(),
                hasOverride = container.baseUrlStore.hasOverride(),
                onDismiss = { showServerDialog = false },
                onSave = { url ->
                    container.baseUrlStore.save(url)
                    showServerDialog = false
                    killAppForRestart()
                },
                onReset = {
                    container.baseUrlStore.clear()
                    showServerDialog = false
                    killAppForRestart()
                }
            )
        }
    }
}

/**
 * Di&aacute;logo para cambiar la URL del backend desde el Login. Guarda en
 * SharedPreferences y mata el proceso para que AppContainer se reconstruya
 * con la nueva URL en el pr&oacute;ximo arranque.
 */
@Composable
private fun ServerUrlDialog(
    current: String,
    default: String,
    hasOverride: Boolean,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onReset: () -> Unit
) {
    var input by remember { mutableStateOf(current) }
    val trimmed = input.trim()
    val isValid = BaseUrlStore.isValid(trimmed)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Servidor de Musarum") },
        text = {
            Column {
                Text(
                    "URL del backend al que conectar. Cambiarla cierra la " +
                        "app para aplicar; vuélvela a abrir para entrar con la nueva.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    label = { Text("URL") },
                    placeholder = { Text("https://musarum.example.com") },
                    singleLine = true,
                    isError = input.isNotBlank() && !isValid,
                    supportingText = {
                        if (input.isNotBlank() && !isValid) {
                            Text("Debe empezar por http:// o https://")
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    if (hasOverride) "Por defecto: $default" else "Por defecto activo",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = {
                    if (trimmed != current.trim()) onSave(trimmed) else onDismiss()
                }
            ) { Text("Guardar") }
        },
        dismissButton = {
            if (hasOverride) {
                TextButton(onClick = onReset) { Text("Usar por defecto") }
            } else {
                TextButton(onClick = onDismiss) { Text("Cancelar") }
            }
        }
    )
}

/**
 * Mata el proceso para forzar que AppContainer se reconstruya con la nueva
 * baseUrl. No hay forma limpia de cambiar la baseUrl de Retrofit en caliente
 * (el {@link retrofit2.Retrofit} es inmutable tras {@code build()}). El user
 * pulsa el icono de la app en el launcher para reentrar.
 */
private fun killAppForRestart() {
    android.os.Process.killProcess(android.os.Process.myPid())
}

/**
 * Fila horizontal scrollable de usuarios con avatar + nick, estilo Netflix.
 * Al tapear uno, el caller rellena el nick y mueve el foco al password.
 * El usuario "seleccionado" (el que coincide con el nick tecleado) se
 * destaca con un borde primary más grueso y una leve escala; los demás
 * quedan con un halo onSurfaceVariant tenue.
 */
@Composable
private fun UserPickerRow(
    users: List<LoginUserDto>,
    selectedNick: String,
    onSelect: (LoginUserDto) -> Unit
) {
    val scrollState = rememberScrollState()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Spacer(Modifier.width(4.dp))
        for (u in users) {
            UserPickerBubble(
                user = u,
                selected = u.nick.equals(selectedNick.trim(), ignoreCase = true),
                onClick = { onSelect(u) }
            )
        }
        Spacer(Modifier.width(4.dp))
    }
}

@Composable
private fun UserPickerBubble(
    user: LoginUserDto,
    selected: Boolean,
    onClick: () -> Unit
) {
    val size = if (selected) 76.dp else 64.dp
    val primary = MaterialTheme.colorScheme.primary
    val bg = MaterialTheme.colorScheme.surfaceVariant
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(84.dp)
            .clickable(onClick = onClick)
    ) {
        Surface(
            shape = CircleShape,
            color = bg,
            border = androidx.compose.foundation.BorderStroke(
                width = if (selected) 3.dp else 1.dp,
                color = if (selected) primary else primary.copy(alpha = 0.25f)
            ),
            modifier = Modifier.size(size)
        ) {
            if (user.avatarUrl.isNullOrBlank()) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(size * 0.5f)
                    )
                }
            } else {
                // Reutilizamos ArtistAvatar (coincide: círculo + Coil + fallback).
                ArtistAvatar(
                    url = user.avatarUrl,
                    size = size
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = user.nick,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = if (selected) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}
