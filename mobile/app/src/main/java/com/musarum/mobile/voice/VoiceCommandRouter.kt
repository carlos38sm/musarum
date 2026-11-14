package com.musarum.mobile.voice

import com.musarum.mobile.data.music.PlaylistRepository
import com.musarum.mobile.data.music.SearchRepository
import com.musarum.mobile.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Enruta un texto reconocido por voz a una acci&oacute;n concreta sobre el player.
 * Idioma de arranque: castellano, con fallback a ingl&eacute;s pelado en los
 * verbos comunes (play / pause / next / previous). No usamos NLU pesado: una
 * lista de regex ordenada cubre las intenciones m&aacute;s frecuentes.
 *
 * Comandos soportados (castellano + ingl&eacute;s simple):
 *   - pausa / páusalo / para / pause / stop → pause()
 *   - play / reanudar / sigue / dale / resume → togglePlayPause()
 *   - siguiente / próxima / salta / next → next()
 *   - anterior / atrás / previo / previous → previous()
 *   - aleatorio / shuffle → toggleShuffle()
 *   - reproduce X / pon X / play X / escucha X → búsqueda + reproducir
 *   - pon la playlist X / reproduce playlist X → buscar playlist y reproducir
 *
 * Si no casa con nada, {@code onFallbackSearch} recibe el texto crudo para
 * meterlo en la barra de búsqueda (Carlos puede rematarlo a mano).
 */
class VoiceCommandRouter(
    private val controller: PlayerController,
    private val searchRepository: SearchRepository,
    private val playlistRepository: PlaylistRepository,
    private val scope: CoroutineScope
) {

    /**
     * Ejecuta el comando y llama a {@code report} con un mensaje humano para
     * snackbar.
     */
    fun route(
        rawText: String,
        onFallbackSearch: (String) -> Unit,
        report: (String) -> Unit
    ) {
        val text = rawText.trim().lowercase()
        if (text.isEmpty()) return

        // ── Comandos sin parámetros ──
        // PlayerController expone togglePlayPause pero no pause/play explícitos.
        // Para "pausa" y "reanudar" delegamos en toggle: si el usuario dice
        // "pausa" estando ya en pausa, el player sigue igual (aceptable).
        when {
            PAUSE.containsMatchIn(text) || PLAY.containsMatchIn(text) -> {
                controller.togglePlayPause()
                report(if (PAUSE.containsMatchIn(text)) "Pausado" else "Reanudado")
                return
            }
            NEXT.containsMatchIn(text) -> {
                controller.next()
                report("Siguiente")
                return
            }
            PREV.containsMatchIn(text) -> {
                controller.previous()
                report("Anterior")
                return
            }
            SHUFFLE.containsMatchIn(text) -> {
                controller.toggleShuffle()
                report("Aleatorio alternado")
                return
            }
        }

        // ── Comandos con parámetro: "pon X" / "reproduce X" / "playlist X" ──
        val playlistTarget = PLAYLIST_CMD.find(text)?.groupValues?.get(1)?.trim()
        if (!playlistTarget.isNullOrBlank()) {
            scope.launch {
                runCatching {
                    val lists = playlistRepository.refresh()
                    val match = lists.firstOrNull {
                        it.name.lowercase().contains(playlistTarget)
                    }
                    if (match == null) {
                        report("No encontré ninguna playlist con \"$playlistTarget\"")
                        return@launch
                    }
                    val full = playlistRepository.details(match.id)
                    val songs = full.entries.content.map { it.toSongSummary() }
                    if (songs.isEmpty()) {
                        report("La playlist \"${match.name}\" está vacía")
                    } else {
                        controller.playQueue(songs, 0)
                        report("Reproduciendo \"${match.name}\"")
                    }
                }.onFailure {
                    report("No se pudo reproducir la playlist")
                }
            }
            return
        }

        val playTarget = PLAY_CMD.find(text)?.groupValues?.get(1)?.trim()
        if (!playTarget.isNullOrBlank()) {
            scope.launch {
                runCatching {
                    val results = searchRepository.search(playTarget)
                    val song = results.songs.firstOrNull()
                    if (song != null) {
                        controller.playQueue(listOf(song), 0)
                        report("Reproduciendo \"${song.title}\"")
                    } else {
                        onFallbackSearch(playTarget)
                        report("No encontré \"$playTarget\"; busco por ti")
                    }
                }.onFailure {
                    onFallbackSearch(playTarget)
                    report("Error buscando; abierta la búsqueda")
                }
            }
            return
        }

        // Sin comando claro → metemos el texto crudo en la barra de búsqueda.
        onFallbackSearch(rawText.trim())
        report("Buscando: ${rawText.trim()}")
    }

    companion object {
        // Palabras aisladas o al principio de frase: evita falsos positivos con
        // "pausa prolongada" cuando quisiera decir otra cosa — en la práctica
        // los comandos de voz son monosilábicos así que la regex es laxa.
        private val PAUSE = Regex("\\b(pausa|p[aá]usa(?:lo)?|para|pause|stop)\\b")
        private val PLAY = Regex("\\b(play|reanudar|sigue|dale|resume|contin[uú]a)\\b")
        private val NEXT = Regex("\\b(siguiente|pr[oó]xima|salta|next|cambia|skip)\\b")
        private val PREV = Regex("\\b(anterior|atr[aá]s|previo|previous|back)\\b")
        private val SHUFFLE = Regex("\\b(aleatorio|shuffle|mezcla)\\b")
        private val PLAYLIST_CMD = Regex(
            "(?:pon|reproduce|play)\\s+(?:la\\s+)?(?:lista|playlist)\\s+(.+)$"
        )
        private val PLAY_CMD = Regex(
            "(?:pon|reproduce|escucha|play)\\s+(.+)$"
        )
    }
}
