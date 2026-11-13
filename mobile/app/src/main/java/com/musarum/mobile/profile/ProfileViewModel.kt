package com.musarum.mobile.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.network.dto.ScannerStatusDto
import com.musarum.mobile.data.network.dto.UserResponseDto
import com.musarum.mobile.data.network.dto.UserStatsDto
import com.musarum.mobile.data.user.UserRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ProfileUiState(
    val loading: Boolean = true,
    val user: UserResponseDto? = null,
    val stats: UserStatsDto? = null,
    val error: String? = null,
    val savingNick: Boolean = false,
    val changingPassword: Boolean = false,
    val scanning: Boolean = false,
    val scannerStatus: ScannerStatusDto? = null,
    val lastMessage: String? = null
)

class ProfileViewModel(private val repository: UserRepository) : ViewModel() {

    private val _state = MutableStateFlow(ProfileUiState())
    val state: StateFlow<ProfileUiState> = _state.asStateFlow()

    // Job del poller del scanner. Lo guardamos para cancelarlo cuando running
    // pasa a false, y para no lanzar dos pollers simultáneos.
    private var scannerPollJob: Job? = null

    init {
        refresh()
        // Al abrir Perfil comprobamos una vez si hay un escaneo en curso
        // (p. ej. el usuario lo lanzó, cerró la app y volvió). Si está vivo,
        // arrancamos el poller.
        pollScannerOnce()
    }

    private fun pollScannerOnce() {
        viewModelScope.launch {
            runCatching { repository.scannerStatus() }
                .onSuccess { s ->
                    _state.value = _state.value.copy(scannerStatus = s, scanning = s.running)
                    if (s.running) startScannerPolling()
                }
        }
    }

    private fun startScannerPolling() {
        if (scannerPollJob?.isActive == true) return
        scannerPollJob = viewModelScope.launch {
            while (true) {
                val s = runCatching { repository.scannerStatus() }.getOrNull()
                if (s != null) {
                    _state.value = _state.value.copy(
                        scannerStatus = s,
                        scanning = s.running
                    )
                    if (!s.running) break
                }
                delay(1500)
            }
            scannerPollJob = null
        }
    }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            try {
                val user = repository.refresh()
                val stats = runCatching { repository.stats() }.getOrNull()
                _state.value = _state.value.copy(
                    loading = false,
                    user = user,
                    stats = stats
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = _state.value.copy(loading = false, error = t.message ?: "Error")
            }
        }
    }

    fun triggerScan(onDone: (Boolean, String) -> Unit) {
        if (_state.value.scanning) return
        _state.value = _state.value.copy(scanning = true)
        viewModelScope.launch {
            try {
                repository.triggerScan()
                // Arrancamos el poller justo después del POST. El escaneo
                // corre en segundo plano en el backend; el poller refresca
                // "scanning" y el contador de progreso cada 1.5s.
                startScannerPolling()
                onDone(true, "Escaneo iniciado")
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = _state.value.copy(scanning = false)
                onDone(false, t.message ?: "No se pudo escanear")
            }
        }
    }

    fun updateNick(newNick: String, onDone: (Boolean, String) -> Unit) {
        _state.value = _state.value.copy(savingNick = true)
        viewModelScope.launch {
            runCatching { repository.updateNick(newNick) }
                .onSuccess {
                    _state.value = _state.value.copy(savingNick = false, user = it)
                    onDone(true, "Nick actualizado")
                }
                .onFailure {
                    _state.value = _state.value.copy(savingNick = false)
                    onDone(false, it.message ?: "No se pudo actualizar")
                }
        }
    }

    fun changePassword(current: String, new: String, onDone: (Boolean, String) -> Unit) {
        _state.value = _state.value.copy(changingPassword = true)
        viewModelScope.launch {
            runCatching { repository.changePassword(current, new) }
                .onSuccess {
                    _state.value = _state.value.copy(changingPassword = false)
                    onDone(true, "Contraseña actualizada")
                }
                .onFailure {
                    _state.value = _state.value.copy(changingPassword = false)
                    onDone(false, it.message ?: "No se pudo cambiar la contraseña")
                }
        }
    }

    /**
     * Crea un usuario nuevo. Solo lo invoca la UI cuando el usuario actual
     * es OWNER (botón solo visible entonces). Si el backend devuelve 403
     * — p. ej. porque el role del JWT no es OWNER — mostramos un mensaje.
     */
    fun createUser(nick: String, password: String, asOwner: Boolean,
                   onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            runCatching {
                repository.createUser(
                    nick = nick,
                    password = password,
                    role = if (asOwner) "OWNER" else "MEMBER"
                )
            }.onSuccess { onDone(true, "Usuario ${it.nick} creado") }
             .onFailure { onDone(false, it.message ?: "No se pudo crear el usuario") }
        }
    }

    /** Sube una imagen como avatar del usuario autenticado. */
    fun uploadAvatar(bytes: ByteArray, mime: String, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.uploadAvatar(bytes, mime) }
                .onSuccess {
                    _state.value = _state.value.copy(user = it)
                    // Hacemos un refresh desde /me para blindarnos: si por lo
                    // que sea el UPDATE del avatar_url no quedó persistido
                    // (contención con scanner, etc.), el GET nos devuelve el
                    // estado real desde DB y la UI no se queda con la foto
                    // vieja. Idempotente; coste mínimo.
                    runCatching { repository.refresh() }.onSuccess { fresh ->
                        _state.value = _state.value.copy(user = fresh)
                    }
                    onDone(true, "Foto actualizada")
                }
                .onFailure { onDone(false, it.message ?: "No se pudo subir la foto") }
        }
    }

    fun clearAvatar(onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.deleteAvatar() }
                .onSuccess {
                    _state.value = _state.value.copy(user = it)
                    onDone(true, "Foto eliminada")
                }
                .onFailure { onDone(false, it.message ?: "No se pudo eliminar") }
        }
    }

    /**
     * Toggle optimista para "compartir qué escucho" y "mostrar mis gustos".
     * Si el PATCH falla, {@code UserRepository.updatePrivacy} ya no mutará
     * _me; el switch volverá al valor del backend en el siguiente recompose.
     */
    fun updatePrivacyNowPlaying(value: Boolean) {
        viewModelScope.launch {
            runCatching { repository.updatePrivacy(shareNowPlaying = value) }
        }
    }

    fun updatePrivacyTaste(value: Boolean) {
        viewModelScope.launch {
            runCatching { repository.updatePrivacy(shareTaste = value) }
        }
    }

    companion object {
        fun factory(repository: UserRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ProfileViewModel(repository) as T
            }
    }
}
