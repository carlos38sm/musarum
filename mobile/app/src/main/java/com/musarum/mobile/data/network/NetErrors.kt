package com.musarum.mobile.data.network

import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import retrofit2.HttpException

/**
 * Convierte un Throwable de red en un mensaje legible para el usuario.
 * Pensado para snackbars: breve, sin jerga, sin stack trace. Los sitios
 * que llaman a la API con runCatching pueden pasar el `exceptionOrNull()`
 * por aquí para evitar textos tipo "HTTP 500" o "null".
 */
fun prettyNetError(t: Throwable?): String {
    if (t == null) return "Error desconocido"
    return when (t) {
        is UnknownHostException -> "No se encuentra el servidor"
        is SocketTimeoutException -> "El servidor tardó demasiado"
        is HttpException -> when (t.code()) {
            400 -> "Petición no válida"
            401 -> "Sesión no válida — vuelve a entrar"
            403 -> "No tienes permiso para esto"
            404 -> "No existe o ha desaparecido"
            409 -> "Conflicto — intenta recargar"
            413 -> "El contenido es demasiado grande"
            in 500..599 -> "El servidor tuvo un problema"
            else -> "Servidor: código ${t.code()}"
        }
        is IOException -> "Sin conexión con el servidor"
        else -> t.message?.takeIf { it.isNotBlank() } ?: "Error inesperado"
    }
}
