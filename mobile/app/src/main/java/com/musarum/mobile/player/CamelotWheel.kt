package com.musarum.mobile.player

/**
 * Conversor de tonalidad musical a la rueda de Camelot (notación DJ).
 * Admite entradas tipo "C", "C maj", "C major", "Cm", "C min", "C minor",
 * "C#m", "Db maj", "A min", etc. Devuelve null si no se puede interpretar.
 *
 * Convención Camelot:
 *   - mayores → sufijo "B"
 *   - menores → sufijo "A"
 *   - números del 1 al 12, uno por quinta justa
 */
object CamelotWheel {

    private val MAJOR = mapOf(
        "C" to "8B", "G" to "9B", "D" to "10B", "A" to "11B", "E" to "12B",
        "B" to "1B", "F#" to "2B", "Gb" to "2B", "C#" to "3B", "Db" to "3B",
        "G#" to "4B", "Ab" to "4B", "D#" to "5B", "Eb" to "5B", "A#" to "6B",
        "Bb" to "6B", "F" to "7B"
    )
    private val MINOR = mapOf(
        "A" to "8A", "E" to "9A", "B" to "10A", "F#" to "11A", "Gb" to "11A",
        "C#" to "12A", "Db" to "12A", "G#" to "1A", "Ab" to "1A", "D#" to "2A",
        "Eb" to "2A", "A#" to "3A", "Bb" to "3A", "F" to "4A", "C" to "5A",
        "G" to "6A", "D" to "7A"
    )

    /**
     * @param raw cadena de tonalidad musical (ej: "C maj", "Am", "F#m", "Bb")
     * @return código Camelot (ej: "8B", "11A") o null si no se reconoce
     */
    fun toCamelot(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val cleaned = raw.trim()

        // Detectamos si es menor por sufijo "m", " min", " minor"
        val isMinor = Regex("(?i)\\s*(m|min|minor)$").containsMatchIn(cleaned)
                || Regex("(?i)^[A-G][#b]?\\s*m(?!a)").containsMatchIn(cleaned)

        // Nota raíz: primer carácter + posible # o b
        val noteMatch = Regex("^([A-Ga-g])([#b]?)").find(cleaned) ?: return null
        val note = noteMatch.groupValues[1].uppercase() + noteMatch.groupValues[2]

        return if (isMinor) MINOR[note] else MAJOR[note]
    }
}
