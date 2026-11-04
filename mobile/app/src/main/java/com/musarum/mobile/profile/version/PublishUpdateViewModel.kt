package com.musarum.mobile.profile.version

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.PublishUpdateRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface PublishUpdateUiState {
    data object Loading : PublishUpdateUiState
    data class Ready(
        val currentLatestApk: String?,
        val currentApkUrl: String?,
        val currentMandatory: Boolean,
        val submitting: Boolean = false
    ) : PublishUpdateUiState
    data class Error(val message: String) : PublishUpdateUiState
}

/**
 * ViewModel de la pantalla "Publicar actualización" (OWNER).
 *
 * El estado SOLO guarda lo "server-side" (versión publicada actualmente). Los
 * valores del formulario viven en la propia Composable como rememberSaveable
 * para que cambios de rotación no los pierdan y se puedan editar libremente.
 */
class PublishUpdateViewModel(
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow<PublishUpdateUiState>(PublishUpdateUiState.Loading)
    val state: StateFlow<PublishUpdateUiState> = _state.asStateFlow()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            _state.value = PublishUpdateUiState.Loading
            try {
                val current = apiService.currentPublishedUpdate()
                _state.value = PublishUpdateUiState.Ready(
                    currentLatestApk = current.latestApk,
                    currentApkUrl = current.apkUrl,
                    currentMandatory = current.mandatory
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = PublishUpdateUiState.Error(
                    t.message ?: "No se pudo cargar la versión publicada"
                )
            }
        }
    }

    /** Envía el formulario. Llama a onResult con un mensaje para el snackbar. */
    fun publish(
        latestApk: String,
        apkUrl: String,
        mandatory: Boolean,
        onResult: (Boolean, String) -> Unit
    ) {
        val ready = _state.value as? PublishUpdateUiState.Ready ?: return
        if (ready.submitting) return
        _state.value = ready.copy(submitting = true)
        viewModelScope.launch {
            try {
                val saved = apiService.publishUpdate(
                    PublishUpdateRequest(
                        latestApk = latestApk.ifBlank { null },
                        apkUrl = apkUrl.ifBlank { null },
                        mandatory = mandatory
                    )
                )
                _state.value = PublishUpdateUiState.Ready(
                    currentLatestApk = saved.latestApk,
                    currentApkUrl = saved.apkUrl,
                    currentMandatory = saved.mandatory
                )
                val label = saved.latestApk
                onResult(
                    true,
                    if (label.isNullOrBlank()) "Anuncio de actualización retirado"
                    else "Actualización $label publicada"
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = ready.copy(submitting = false)
                onResult(false, t.message ?: "No se pudo publicar la actualización")
            }
        }
    }

    companion object {
        /**
         * Dada una versión "1.0.4" devuelve la URL de descarga canónica del
         * APK que el workflow android-release.yml habría publicado. Sirve
         * solo como sugerencia; el usuario puede editarla antes de enviar.
         */
        fun suggestApkUrl(version: String, repo: String = "carlos38sm/musarum"): String {
            val v = version.trim().removePrefix("v").removePrefix("V")
            if (v.isEmpty()) return ""
            return "https://github.com/$repo/releases/download/android-v$v/musarum-$v.apk"
        }

        fun factory(apiService: ApiService): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    PublishUpdateViewModel(apiService) as T
            }
    }
}
