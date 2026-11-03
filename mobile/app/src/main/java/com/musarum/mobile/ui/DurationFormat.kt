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
