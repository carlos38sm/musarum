package com.musarum.mobile.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback

/**
 * Pequeño vocabulario háptico compartido — centraliza qué tipo de vibración
 * dispara cada interacción para que la UI "se sienta" consistente.
 *
 * Reglas:
 *   - [tap]              → confirmación corta y sutil. Toggle de favorito,
 *                          switch, botón primario.
 *   - [longPress]        → vibración más pesada antes de abrir un sheet /
 *                          entrar en modo edición. La usamos en long-press
 *                          de SongRow y en el inicio de un drag-and-drop.
 *   - [success]          → confirma una acción completada (p. ej. descarga
 *                          marcada como permanente). Más sonora que [tap].
 *
 * Implementación: usamos [LocalHapticFeedback]. Android soporta oficialmente
 * dos tipos en Compose ([HapticFeedbackType.TextHandleMove] y
 * [HapticFeedbackType.LongPress]); los mapeamos así porque son los dos
 * únicos patrones que Android garantiza en todos los móviles de nuestros 4
 * users. En ROMs sin motor háptico (emulador, algunos tablets), estas
 * llamadas son no-ops.
 */
class MusarumHaptics internal constructor(private val raw: HapticFeedback) {
    fun tap() = raw.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    fun longPress() = raw.performHapticFeedback(HapticFeedbackType.LongPress)
    fun success() = raw.performHapticFeedback(HapticFeedbackType.LongPress)
}

@Composable
fun rememberMusarumHaptics(): MusarumHaptics {
    val raw = LocalHapticFeedback.current
    return MusarumHaptics(raw)
}
