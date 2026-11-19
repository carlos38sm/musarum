package com.musarum.mobile.ui

/**
 * Convierte una duración acumulada (en milisegundos) a texto humanizado:
 *   < 60 min      → "47 min"
 *   < 24 h        → "3 h 12 min"  (sin minutos si son 0 → "3 h")
 *   ≥ 24 h        → "2 d 5 h"      (sin horas si son 0 → "2 d")
 *
 * Para contadores de biblioteca donde "2.872.382 min" es un número ilegible.
 */
fun humanizeDurationMs(totalMs: Long): String {
    if (totalMs <= 0L) return "0 min"
    val totalMinutes = totalMs / 60_000L
    if (totalMinutes < 60L) return "$totalMinutes min"
    val totalHours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    if (totalHours < 24L) {
        return if (minutes == 0L) "$totalHours h" else "$totalHours h $minutes min"
    }
    val days = totalHours / 24L
    val hours = totalHours % 24L
    return if (hours == 0L) "$days d" else "$days d $hours h"
}

/**
 * Formatea un timestamp ISO-8601 que viene del backend (ej.
 * "2026-11-14T18:32:04.123Z") como fecha corta en castellano. Si no
 * parsea, devuelve la cadena cruda — no queremos crashear el header
 * de la playlist por un formato inesperado.
 */
fun humanizeIso(iso: String): String {
    return runCatching {
        val instant = java.time.Instant.parse(iso)
        val localDate = instant.atZone(java.time.ZoneId.systemDefault()).toLocalDate()
        val months = arrayOf("ene", "feb", "mar", "abr", "may", "jun",
                             "jul", "ago", "sep", "oct", "nov", "dic")
        "${localDate.dayOfMonth} ${months[localDate.monthValue - 1]} ${localDate.year}"
    }.getOrElse { iso }
}

/**
 * Formatea un timestamp como "última conexión" en relativo: "ahora",
 * "hace X min", "hace X h", "hace X d" (hasta 30 días) y fecha corta
 * más allá. Si viene null o no parsea, devuelve "Nunca".
 */
fun humanizeLastSeen(iso: String?): String {
    if (iso.isNullOrBlank()) return "Nunca"
    return runCatching {
        val instant = java.time.Instant.parse(iso)
        val diffMs = System.currentTimeMillis() - instant.toEpochMilli()
        val mins = diffMs / 60_000L
        val hours = diffMs / 3_600_000L
        val days = diffMs / 86_400_000L
        when {
            mins < 2 -> "ahora"
            mins < 60 -> "hace $mins min"
            hours < 24 -> "hace $hours h"
            days < 30 -> "hace $days d"
            else -> humanizeIso(iso)
        }
    }.getOrElse { "Nunca" }
}
