package com.musarum.mobile.profile.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.network.dto.UserResponseDto
import com.musarum.mobile.data.user.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class UsersUiState(
    val loading: Boolean = true,
    val users: List<UserResponseDto> = emptyList(),
    val error: String? = null
)

/**
 * Gestión de usuarios desde Perfil (solo OWNER). Expone acciones de bloquear/
 * desbloquear y eliminar. Las protecciones (no borrarse a uno mismo, no
 * borrar al último OWNER) viven en el backend; aquí solo propagamos el
 * mensaje al snackbar.
 */
class UsersViewModel(private val repository: UserRepository) : ViewModel() {

    private val _state = MutableStateFlow(UsersUiState())
    val state: StateFlow<UsersUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        _state.value = _state.value.copy(loading = true, error = null)
        viewModelScope.launch {
            runCatching { repository.listAll() }
                .onSuccess { _state.value = UsersUiState(false, it, null) }
                .onFailure {
                    _state.value = UsersUiState(false, emptyList(), it.message ?: "Error")
                }
        }
    }

    fun toggleBlocked(id: Long, blocked: Boolean, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.setBlocked(id, blocked) }
                .onSuccess { updated ->
                    _state.value = _state.value.copy(
                        users = _state.value.users.map {
                            if (it.id == id) updated else it
                        }
                    )
                    onDone(true, if (blocked) "Usuario bloqueado" else "Usuario desbloqueado")
                }
                .onFailure { onDone(false, it.message ?: "No se pudo cambiar") }
        }
    }

    fun delete(id: Long, onDone: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            runCatching { repository.deleteUser(id) }
                .onSuccess {
                    _state.value = _state.value.copy(
                        users = _state.value.users.filterNot { it.id == id }
                    )
                    onDone(true, "Usuario eliminado")
                }
                .onFailure { onDone(false, it.message ?: "No se pudo eliminar") }
        }
    }

    companion object {
        fun factory(repository: UserRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    UsersViewModel(repository) as T
            }
    }
}
