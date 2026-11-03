package com.musarum.mobile.player

/**
 * Parser de LRC. Convierte texto plano con marcas de tiempo
 * ([mm:ss.xx] línea) en una lista ordenada de {@link LyricLine}.
 *
 * Soporta:
 *  - Timestamps con milisegundos de 2 o 3 dígitos
 *  - Varios timestamps por línea (líneas que se repiten)
 *  - Separadores raros (coma, '.') entre segundos y milis
 *  - Letras sin ningún timestamp → devuelve lista vacía (el UI usará modo plano)
 */
data class LyricLine(val timeMs: Long, val text: String)

object LrcParser {

    private val TIMESTAMP = Regex("""\[(\d{1,2}):(\d{1,2})(?:[.:](\d{1,3}))?\]""")

    fun parse(raw: String?): List<LyricLine> {
        if (raw.isNullOrBlank()) return emptyList()
        val lines = mutableListOf<LyricLine>()

        for (rawLine in raw.lineSequence()) {
            val stamps = TIMESTAMP.findAll(rawLine).toList()
            if (stamps.isEmpty()) continue

            val text = TIMESTAMP.replace(rawLine, "").trim()
            for (match in stamps) {
                val mm = match.groupValues[1].toIntOrNull() ?: continue
                val ss = match.groupValues[2].toIntOrNull() ?: continue
                val ms = parseMillis(match.groupValues.getOrNull(3))
                val timeMs = (mm * 60L + ss) * 1000L + ms
                lines += LyricLine(timeMs, text)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    private fun parseMillis(raw: String?): Long {
        if (raw.isNullOrBlank()) return 0
        return when (raw.length) {
            1 -> raw.toLong() * 100           // "[00:12.3]" → 300ms
            2 -> raw.toLong() * 10            // "[00:12.34]" → 340ms
            else -> raw.take(3).toLong()      // "[00:12.345]" → 345ms
        }
    }

    /** Índice de la línea activa para una posición dada (0..size-1), o -1 si aún no ha empezado. */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int {
        if (lines.isEmpty()) return -1
        // Búsqueda binaria del último timeMs <= positionMs
        var lo = 0
        var hi = lines.size - 1
        var best = -1
        while (lo <= hi) {
            val mid = (lo + hi) ushr 1
            if (lines[mid].timeMs <= positionMs) {
                best = mid
                lo = mid + 1
            } else {
                hi = mid - 1
            }
        }
        return best
    }
}
