package com.musarum.mobile.profile.server

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.musarum.mobile.data.network.ApiService
import com.musarum.mobile.data.network.dto.EnrichProgressDto
import com.musarum.mobile.data.network.dto.HostHistoryDto
import com.musarum.mobile.data.network.dto.HostStatsDto
import com.musarum.mobile.data.network.dto.ServerStatsDto
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface ServerStatsUiState {
    data object Loading : ServerStatsUiState
    data class Ready(val stats: ServerStatsDto) : ServerStatsUiState
    data class Error(val message: String) : ServerStatsUiState
}

/**
 * ViewModel de la pantalla "Análisis del servidor". Carga una sola vez al
 * entrar y permite recargar manualmente desde el botón del TopAppBar.
 */
class ServerStatsViewModel(
    private val apiService: ApiService
) : ViewModel() {

    private val _state = MutableStateFlow<ServerStatsUiState>(ServerStatsUiState.Loading)
    val state: StateFlow<ServerStatsUiState> = _state.asStateFlow()

    // Host stats (CPU/RAM/red del Windows). null = aún no cargadas o el
    // backend es viejo y no expone /host-stats. Separadas del state
    // principal para que el polling live no reinicie las otras tarjetas.
    private val _host = MutableStateFlow<HostStatsDto?>(null)
    val host: StateFlow<HostStatsDto?> = _host.asStateFlow()

    private val _hostLive = MutableStateFlow(false)
    val hostLive: StateFlow<Boolean> = _hostLive.asStateFlow()

    private var hostPollingJob: Job? = null

    // Historial de los últimos 30 min para sparklines. Lo mantenemos aparte
    // del snapshot ad-hoc: el backend muestrea con su propio scheduler y
    // devuelve lo que lleve acumulado. null = aún no se cargó o el backend
    // es viejo y no expone /host-history (cae silencioso y la tarjeta
    // pinta solo los contadores).
    private val _hostHistory = MutableStateFlow<HostHistoryDto?>(null)
    val hostHistory: StateFlow<HostHistoryDto?> = _hostHistory.asStateFlow()
    private var historyPollingJob: Job? = null

    // Progreso del "Re-enriquecer todo". null = nunca disparado desde este
    // dispositivo (o el backend reinició). El polling vive mientras el
    // snapshot indique running=true.
    private val _enrichProgress = MutableStateFlow<EnrichProgressDto?>(null)
    val enrichProgress: StateFlow<EnrichProgressDto?> = _enrichProgress.asStateFlow()
    private var enrichPollingJob: Job? = null

    init {
        refresh()
        loadHostOnce()
        loadHistoryOnce()
        // Si al entrar el backend ya tiene un run en marcha (otro dispositivo
        // lo lanzó), arrancamos el polling solos.
        checkInitialEnrichProgress()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.value = ServerStatsUiState.Loading
            try {
                val stats = apiService.serverStats()
                _state.value = ServerStatsUiState.Ready(stats)
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                _state.value = ServerStatsUiState.Error(
                    t.message ?: "No se pudo cargar el análisis del servidor"
                )
            }
        }
        // Al recargar el panel entero refrescamos también host-stats (si
        // es alcanzable). Las dos llamadas son independientes.
        loadHostOnce()
    }

    /** Pide un snapshot puntual del host. Fire-and-forget: si falla lo deja a null. */
    private fun loadHostOnce() {
        viewModelScope.launch {
            try {
                _host.value = apiService.hostStats()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) {
                // Backend viejo que no expone /host-stats, o 401 — silencioso.
                // La tarjeta se queda sin pintar y no revienta nada.
            }
        }
    }

    /**
     * Carga puntual del historial y arranca polling cada 10s para que las
     * sparklines "avancen" mientras la pantalla esté abierta. Backends que
     * no expongan /host-history dejan el flow en null (lo que la UI usa
     * para decidir si pinta la sparkline o no).
     */
    private fun loadHistoryOnce() {
        // Fetch inicial + polling continuo cada 10s mientras el VM viva.
        // Una muestra nueva entra en el buffer del backend cada 10s, así
        // que pedir más seguido es desperdicio; menos seguido se queda
        // atrasado respecto a los contadores en vivo.
        historyPollingJob?.cancel()
        historyPollingJob = viewModelScope.launch {
            while (true) {
                try {
                    _hostHistory.value = apiService.hostHistory()
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) {
                    // Backend viejo o red caída: deja lo que había
                    // y vuelve a intentarlo.
                }
                delay(10_000)
            }
        }
    }

    /**
     * Enciende o apaga el "modo live" del panel Máquina: mientras esté ON,
     * pide {@code /host-stats} cada 2s. La velocidad instantánea de red e
     * IO solo tiene sentido con refrescos cortos — el backend calcula el
     * delta entre peticiones consecutivas, así que si no pides seguido
     * verás "0 B/s" incluso con tráfico de fondo.
     */
    fun setHostLive(enabled: Boolean) {
        if (_hostLive.value == enabled) return
        _hostLive.value = enabled
        hostPollingJob?.cancel()
        if (enabled) {
            hostPollingJob = viewModelScope.launch {
                while (_hostLive.value) {
                    try {
                        _host.value = apiService.hostStats()
                    } catch (ce: CancellationException) {
                        throw ce
                    } catch (_: Throwable) { /* silencio: en la próxima iteración lo reintenta */ }
                    delay(2000)
                }
            }
        }
    }

    override fun onCleared() {
        hostPollingJob?.cancel()
        historyPollingJob?.cancel()
        enrichPollingJob?.cancel()
        super.onCleared()
    }

    /** Borra los .lrc huérfanos del servidor y refresca el panel. */
    fun cleanOrphanLyrics() {
        viewModelScope.launch {
            try {
                apiService.cleanOrphanLyrics()
            } catch (ce: CancellationException) {
                throw ce
            } catch (_: Throwable) { /* best-effort: en el refresh se verá si quedó algo */ }
            refresh()
        }
    }

    /**
     * Vacía la biblioteca entera (songs/artists/albums/genres + referencias).
     * El TRUNCATE del servidor es prácticamente instantáneo; igualmente
     * marcamos Loading para no mostrar las cifras viejas mientras llega la
     * respuesta. onResult(cuántas canciones había antes, mensaje).
     */
    /**
     * Re-lanza enriquecimiento de Emendatium sobre todas las canciones. El
     * trabajo va fire-and-forget: los datos nuevos (bio, BPM, tonalidad,
     * replaygain) aparecen con el tiempo, no de golpe. No refrescamos el panel
     * en caliente porque las cifras de "Enriquecimiento faltante" no cambian
     * al instante.
     */
    fun reEnrichAll(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val summary = apiService.reEnrichAll()
                // Arrancamos el polling del progreso en cuanto sabemos que
                // la request superó auth y empezó a encolar.
                startEnrichPolling()
                onResult(
                    true,
                    "Encoladas ${summary.queued} canciones. Los datos aparecen poco a poco."
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                onResult(false, t.message ?: "No se pudo re-enriquecer")
            }
        }
    }

    private fun checkInitialEnrichProgress() {
        viewModelScope.launch {
            try {
                val p = apiService.enrichProgress()
                if (p.running) {
                    _enrichProgress.value = p
                    startEnrichPolling()
                } else if (p.finishedAtMs != null) {
                    // Terminado hace un rato: lo mostramos como resumen hasta
                    // que el usuario lo descarte o lance otro run.
                    _enrichProgress.value = p
                }
            } catch (_: Throwable) { /* backend viejo o sin auth: silencio */ }
        }
    }

    private fun startEnrichPolling() {
        enrichPollingJob?.cancel()
        enrichPollingJob = viewModelScope.launch {
            while (true) {
                try {
                    val p = apiService.enrichProgress()
                    _enrichProgress.value = p
                    if (!p.running) break
                } catch (ce: CancellationException) {
                    throw ce
                } catch (_: Throwable) { /* reintenta en el siguiente tick */ }
                delay(2000)
            }
        }
    }

    /**
     * El usuario descarta el resumen final (card de "Hecho · N/N en Xm").
     * Mantener el resumen residual puede confundir si vuelves a Mantenimiento
     * horas después.
     */
    fun dismissEnrichProgress() {
        _enrichProgress.value = null
    }

    /**
     * Cancela el run en curso. El backend marca la bandera cancelled;
     * las tareas @Async aún no arrancadas saldrán sin tocar Emendatium,
     * y las que ya están corriendo terminarán por sí solas (no se abortan
     * HTTP en vuelo). En pocos segundos la barra llega al 100% y pasa
     * a Finalizado.
     */
    fun cancelEnrichment(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                apiService.cancelEnrichment()
                onResult(true, "Cancelando… las tareas en vuelo acabarán, el resto se saltará.")
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                onResult(false, t.message ?: "No se pudo cancelar")
            }
        }
    }

    /**
     * Barre artistas / álbumes / géneros sin canciones. Tras borrar
     * canciones pueden quedar como "fantasmas" en las listas. Refresca el
     * panel al terminar para que el OWNER vea el nuevo conteo.
     */
    fun cleanOrphanEntities(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            try {
                val s = apiService.cleanOrphanEntities()
                onResult(
                    true,
                    "Limpiado · ${s.artists} artistas · ${s.albums} álbumes · ${s.genres} géneros"
                )
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                onResult(false, t.message ?: "No se pudo limpiar")
            }
            refresh()
        }
    }

    fun wipeLibrary(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            _state.value = ServerStatsUiState.Loading
            try {
                val summary = apiService.wipeLibrary()
                onResult(true, "Biblioteca vaciada · ${summary.songs} canciones borradas")
            } catch (ce: CancellationException) {
                throw ce
            } catch (t: Throwable) {
                onResult(false, t.message ?: "No se pudo vaciar la biblioteca")
            }
            refresh()
        }
    }

    companion object {
        fun factory(apiService: ApiService): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ServerStatsViewModel(apiService) as T
            }
    }
}
