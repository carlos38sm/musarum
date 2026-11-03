package com.musarum.mobile.data.downloads

/**
 * Metadatos de una canción ya descargada. Se persiste en un JSON (index.json)
 * dentro de la carpeta privada de la app (filesDir/downloads/). No se guarda
 * en SharedPreferences para evitar el límite práctico de ese almacén si en el
 * futuro añadimos más campos.
 *
 * {@code expiresAt == null} significa "permanente, no caduca". Las descargas
 * temporales nacen con {@code expiresAt = downloadedAt + 24h} y se borran en
 * el próximo arranque tras caducar (nunca si están sonando en ese momento).
 */
data class DownloadInfo(
    val songId: Long,
    val fileName: String,       // "{songId}.mp3", sin ruta — se resuelve contra downloads/
    val downloadedAt: Long,     // epoch ms
    val expiresAt: Long?,       // epoch ms, null = permanente
    val sizeBytes: Long,
    val title: String,          // snapshot al descargar — para listado offline sin backend
    val artistsLabel: String
) {
    val isTemporary: Boolean get() = expiresAt != null
}
