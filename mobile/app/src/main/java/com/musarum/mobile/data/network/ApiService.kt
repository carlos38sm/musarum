package com.musarum.mobile.data.network

import com.musarum.mobile.data.network.dto.ChangePasswordRequest
import com.musarum.mobile.data.network.dto.UpdateUserRequest
import com.musarum.mobile.data.network.dto.UserResponseDto
import com.musarum.mobile.data.network.dto.UserStatsDto
import com.musarum.mobile.data.network.dto.AlbumCardDto
import com.musarum.mobile.data.network.dto.AlbumDetailsDto
import com.musarum.mobile.data.network.dto.AlbumSummaryDto // mantenido por si otros endpoints lo usan
import com.musarum.mobile.data.network.dto.ArtistDetailsDto
import com.musarum.mobile.data.network.dto.ArtistSummaryDto
import com.musarum.mobile.data.network.dto.AddCollaboratorRequest
import com.musarum.mobile.data.network.dto.AddSongToPlaylistRequest
import com.musarum.mobile.data.network.dto.FavoriteDto
import com.musarum.mobile.data.network.dto.GenreDto
import com.musarum.mobile.data.network.dto.LogPlayRequest
import com.musarum.mobile.data.network.dto.LoginRequest
import com.musarum.mobile.data.network.dto.MarkFavoriteRequest
import com.musarum.mobile.data.network.dto.PageDto
import com.musarum.mobile.data.network.dto.PlaylistCreateRequest
import com.musarum.mobile.data.network.dto.PlaylistDetailsDto
import com.musarum.mobile.data.network.dto.PlaylistSummaryDto
import com.musarum.mobile.data.network.dto.PlaylistUpdateRequest
import com.musarum.mobile.data.network.dto.SearchResultDto
import com.musarum.mobile.data.network.dto.SongDetailsDto
import com.musarum.mobile.data.network.dto.SongSummaryDto
import com.musarum.mobile.data.network.dto.TokenResponse
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {

    @POST("auth/login")
    suspend fun login(@Body request: LoginRequest): TokenResponse

    // Picker estilo Netflix: lista de usuarios con avatar para el Login.
    // Ruta pública bajo /auth/** (permitAll). El backend solo manda
    // id + nick + avatarUrl, nunca role ni blocked.
    @GET("auth/users")
    suspend fun getLoginUsers(): List<com.musarum.mobile.data.network.dto.LoginUserDto>

    @GET("songs")
    suspend fun getSongs(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
        @Query("sort") sort: String = "title,asc"
    ): PageDto<SongSummaryDto>

    @GET("songs/{id}")
    suspend fun getSong(@Path("id") id: Long): SongDetailsDto

    @retrofit2.http.Headers("Accept: text/plain")
    @GET("songs/{id}/lyrics")
    suspend fun getLyrics(@Path("id") id: Long): String

    // Fuerza re-búsqueda online ignorando el cache; 204 → no cambió nada.
    @retrofit2.http.Headers("Accept: text/plain")
    @retrofit2.http.POST("songs/{id}/lyrics/refresh")
    suspend fun refreshLyrics(@Path("id") id: Long): retrofit2.Response<String>

    // Reordenar canciones dentro de una playlist (drag-and-drop del móvil).
    @retrofit2.http.PATCH("playlists/{id}/entries/{entryId}/position")
    suspend fun moveEntry(
        @Path("id") playlistId: Long,
        @Path("entryId") entryId: Long,
        @Body request: com.musarum.mobile.data.network.dto.MoveEntryRequest
    ): com.musarum.mobile.data.network.dto.PlaylistDetailsDto

    @GET("songs/search")
    suspend fun searchSongs(
        @Query("q") query: String,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 50
    ): PageDto<SongSummaryDto>

    @GET("songs/shuffled")
    suspend fun getShuffledSongs(): List<SongSummaryDto>

    // Catálogo entero ordenado. Lo usa Canciones al tocar una canción para
    // que la cola del player sea TODO el catálogo (y el shuffle de NowPlaying
    // barajee las 7000, no solo la ventana cargada).
    @GET("songs/all")
    suspend fun getAllSongs(): List<SongSummaryDto>

    // Posición absoluta de la primera canción por letra, para el jump bar.
    @GET("songs/letter-offsets")
    suspend fun getLetterOffsets(): Map<String, Int>

    // Posición absoluta (0-based) de una canción en la vista paginada por
    // defecto. Se usa para "ir a la canción que suena" desde la lista.
    @GET("songs/{id}/position")
    suspend fun getSongPosition(
        @Path("id") id: Long
    ): com.musarum.mobile.data.network.dto.SongPositionDto

    // Borrado completo (fila DB + fichero del disco). Solo OWNER.
    @DELETE("songs/{id}")
    suspend fun deleteSong(@Path("id") id: Long)

    // Edición manual de tags ID3 + metadatos JPA. OWNER-only; el backend
    // reescribe el fichero del disco con jaudiotagger antes de tocar la DB.
    @PATCH("songs/{id}/tags")
    suspend fun editTags(
        @Path("id") id: Long,
        @Body request: com.musarum.mobile.data.network.dto.TagEditRequest
    ): SongDetailsDto

    // Resolución del videoId de YouTube para la canción (cacheado en DB).
    // 204 → no se encontró vídeo; el móvil ocultará el botón.
    @GET("songs/{id}/youtube-video")
    suspend fun getYoutubeVideo(
        @Path("id") id: Long,
        @Query("refresh") refresh: Boolean = false
    ): retrofit2.Response<com.musarum.mobile.data.network.dto.YoutubeVideoDto>

    // ── Bandeja de canciones compartidas ──
    @GET("me/inbox")
    suspend fun getInbox(): List<com.musarum.mobile.data.network.dto.InboxItemDto>

    @GET("me/inbox/unread-count")
    suspend fun getInboxUnreadCount(): Map<String, Long>

    @POST("me/inbox")
    suspend fun sendInbox(
        @Body req: com.musarum.mobile.data.network.dto.SendShareRequest
    ): retrofit2.Response<Unit>

    @POST("me/inbox/{id}/read")
    suspend fun markInboxRead(@Path("id") id: Long): retrofit2.Response<Unit>

    @DELETE("me/inbox/{id}")
    suspend fun deleteInbox(@Path("id") id: Long): retrofit2.Response<Unit>

    // ── Handoff de reproducción entre dispositivos ──
    @POST("handoff/publish")
    suspend fun publishHandoff(
        @Body req: com.musarum.mobile.data.network.dto.PublishHandoffRequest
    ): retrofit2.Response<Unit>

    @GET("handoff")
    suspend fun listHandoffs(): List<com.musarum.mobile.data.network.dto.HandoffDto>

    @DELETE("handoff/publish")
    suspend fun clearHandoff(): retrofit2.Response<Unit>

    // ── Backups (OWNER) ──
    @GET("admin/server/backups")
    suspend fun listBackups(): List<com.musarum.mobile.data.network.dto.BackupFileDto>

    @POST("admin/server/backups")
    suspend fun createBackup(): com.musarum.mobile.data.network.dto.BackupFileDto

    // ── Fiesta musical (listening party) ──
    @POST("party")
    suspend fun createParty(): com.musarum.mobile.data.network.dto.PartyDto

    @GET("party/active")
    suspend fun getActiveParty(): retrofit2.Response<com.musarum.mobile.data.network.dto.PartyDto>

    @POST("party/{id}/join")
    suspend fun joinParty(@Path("id") id: Long): retrofit2.Response<Unit>

    @POST("party/{id}/leave")
    suspend fun leaveParty(@Path("id") id: Long): retrofit2.Response<Unit>

    @POST("party/{id}/queue")
    suspend fun addSongToParty(
        @Path("id") id: Long,
        @Body req: com.musarum.mobile.data.network.dto.AddPartySongRequest
    ): retrofit2.Response<Unit>

    @POST("party/{id}/current")
    suspend fun setPartyCurrent(
        @Path("id") id: Long,
        @Body req: com.musarum.mobile.data.network.dto.SetPartyCurrentRequest
    ): retrofit2.Response<Unit>

    @DELETE("party/{id}")
    suspend fun closeParty(@Path("id") id: Long): retrofit2.Response<Unit>

    // Cola "radio" basada en una canción semilla.
    @GET("songs/{id}/radio")
    suspend fun getRadioFrom(
        @Path("id") id: Long,
        @Query("size") size: Int = 50
    ): List<SongSummaryDto>

    @GET("albums")
    suspend fun getAlbums(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
        @Query("sort") sort: String = "title,asc"
    ): PageDto<AlbumCardDto>

    @GET("albums/{id}")
    suspend fun getAlbum(@Path("id") id: Long): AlbumDetailsDto

    @GET("albums/{id}/songs")
    suspend fun getAlbumSongs(
        @Path("id") id: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 200,
        @Query("sort") sort: String = "trackNumber,asc"
    ): PageDto<SongSummaryDto>

    @GET("artists")
    suspend fun getArtists(
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100,
        @Query("sort") sort: String = "name,asc"
    ): PageDto<ArtistSummaryDto>

    @GET("artists/{id}")
    suspend fun getArtist(@Path("id") id: Long): ArtistDetailsDto

    @GET("artists/{id}/albums")
    suspend fun getArtistAlbums(
        @Path("id") id: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 100
    ): PageDto<AlbumCardDto>

    @GET("artists/{id}/songs")
    suspend fun getArtistSongs(
        @Path("id") id: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 500,
        @Query("sort") sort: String = "title,asc"
    ): PageDto<SongSummaryDto>

    // Artistas que han compartido canciones con éste. El backend
    // devuelve una lista plana (no PageDto) ordenada por frecuencia
    // de colaboración. Lo pinta el móvil como carrusel horizontal
    // "Artistas relacionados" al final de ArtistDetailScreen.
    @GET("artists/{id}/related")
    suspend fun getRelatedArtists(
        @Path("id") id: Long,
        @Query("limit") limit: Int = 20
    ): List<ArtistSummaryDto>

    @GET("search")
    suspend fun search(@Query("q") query: String): SearchResultDto

    @GET("genres")
    suspend fun getGenres(): List<GenreDto>

    @GET("favorites")
    suspend fun getFavorites(): List<FavoriteDto>

    @POST("favorites")
    suspend fun markFavorite(@Body request: MarkFavoriteRequest): FavoriteDto

    @DELETE("favorites/{songId}")
    suspend fun unmarkFavorite(@Path("songId") songId: Long)

    @GET("playlists")
    suspend fun getPlaylists(): List<PlaylistSummaryDto>

    // Playlists en las que SOY colaborador (otro user me invitó). El
    // backend las separa del /playlists "mías" para que la Biblioteca
    // pinte dos secciones distintas sin confusión de propiedad.
    @GET("playlists/shared")
    suspend fun getSharedPlaylists(): List<PlaylistSummaryDto>

    @GET("playlists/{id}")
    suspend fun getPlaylist(
        @Path("id") id: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 200
    ): PlaylistDetailsDto

    @POST("playlists")
    suspend fun createPlaylist(@Body request: PlaylistCreateRequest): PlaylistDetailsDto

    @retrofit2.http.PUT("playlists/{id}")
    suspend fun updatePlaylist(
        @Path("id") id: Long,
        @Body request: PlaylistUpdateRequest
    ): PlaylistDetailsDto

    @DELETE("playlists/{id}")
    suspend fun deletePlaylist(@Path("id") id: Long)

    @POST("playlists/{id}/songs")
    suspend fun addSongToPlaylist(
        @Path("id") id: Long,
        @Body request: AddSongToPlaylistRequest
    ): PlaylistDetailsDto

    // Añadir N canciones de una vez (selección múltiple en móvil).
    // Las que ya estén en la playlist se saltan en el backend.
    @POST("playlists/{id}/songs/bulk")
    suspend fun addSongsToPlaylistBulk(
        @Path("id") id: Long,
        @Body request: com.musarum.mobile.data.network.dto.AddSongsBulkRequest
    ): PlaylistDetailsDto

    @DELETE("playlists/{id}/entries/{entryId}")
    suspend fun removeSongFromPlaylist(
        @Path("id") id: Long,
        @Path("entryId") entryId: Long
    ): PlaylistDetailsDto

    @POST("playlists/{id}/collaborators")
    suspend fun addCollaborator(
        @Path("id") id: Long,
        @Body request: AddCollaboratorRequest
    ): PlaylistDetailsDto

    @DELETE("playlists/{id}/collaborators/{userId}")
    suspend fun removeCollaborator(
        @Path("id") id: Long,
        @Path("userId") userId: Long
    ): PlaylistDetailsDto

    @GET("users/search")
    suspend fun searchUsers(
        @Query("q") query: String,
        @Query("limit") limit: Int = 10
    ): List<UserResponseDto>

    // ── Historial de reproducciones ──

    @POST("me/plays")
    suspend fun logPlay(@Body request: LogPlayRequest)

    @GET("me/plays/top")
    suspend fun getTopPlays(
        @Query("range") range: String = "week",
        @Query("limit") limit: Int = 20
    ): List<SongSummaryDto>

    @GET("me/plays/recent")
    suspend fun getRecentPlays(
        @Query("limit") limit: Int = 20
    ): List<SongSummaryDto>

    @GET("me/plays/last")
    suspend fun getLastPlayed(
        @Query("limit") limit: Int = 1
    ): List<SongSummaryDto>

    // ── Perfil del usuario autenticado ──

    @GET("users/me")
    suspend fun getMe(): UserResponseDto

    @PATCH("users/me")
    suspend fun updateMe(@Body request: UpdateUserRequest): UserResponseDto

    @PATCH("users/me/privacy")
    suspend fun updateMyPrivacy(
        @Body request: com.musarum.mobile.data.network.dto.UpdatePrivacyRequest
    ): UserResponseDto

    @PATCH("users/me/password")
    suspend fun changePassword(@Body request: ChangePasswordRequest)

    @GET("users/me/stats")
    suspend fun getMyStats(): UserStatsDto

    // Alta de usuario — solo OWNER. 403 si lo intenta un MEMBER.
    @POST("users")
    suspend fun createUser(@Body request: com.musarum.mobile.data.network.dto.CreateUserRequest): UserResponseDto

    // Avatar propio.
    @retrofit2.http.Multipart
    @retrofit2.http.PUT("users/me/avatar")
    suspend fun uploadMyAvatar(
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part
    ): UserResponseDto

    @DELETE("users/me/avatar")
    suspend fun deleteMyAvatar(): UserResponseDto

    // Admin de usuarios — solo OWNER.
    @GET("users/admin")
    suspend fun listAllUsers(): List<UserResponseDto>

    @PATCH("users/admin/{id}/blocked")
    suspend fun setUserBlocked(
        @Path("id") id: Long,
        @Query("value") blocked: Boolean
    ): UserResponseDto

    @DELETE("users/admin/{id}")
    suspend fun deleteUser(@Path("id") id: Long)

    // ── Scanner ──
    @POST("scanner/import")
    suspend fun triggerLibraryScan(): retrofit2.Response<String>

    @GET("scanner/status")
    suspend fun scannerStatus(): com.musarum.mobile.data.network.dto.ScannerStatusDto

    // ── Admin: snapshot de salud del servidor (OWNER) ──
    @GET("admin/server/stats")
    suspend fun serverStats(): com.musarum.mobile.data.network.dto.ServerStatsDto

    // Métricas del SO del servidor (CPU, RAM, swap, red, disco IO). Pensado
    // para refresco rápido cuando el OWNER tiene "modo live" ON.
    @GET("admin/server/host-stats")
    suspend fun hostStats(): com.musarum.mobile.data.network.dto.HostStatsDto

    // Subida de un fichero de audio al servidor (OWNER). Multipart
    // con la Part llamada "file" — el backend la lee desde
    // @RequestParam("file"). Respuesta 409 si ya existe por hash.
    @retrofit2.http.Multipart
    @POST("admin/songs/upload")
    suspend fun uploadSong(
        @retrofit2.http.Part file: okhttp3.MultipartBody.Part
    ): retrofit2.Response<com.musarum.mobile.data.network.dto.UploadSongResultDto>

    // Historial agregado de los últimos 30 min para sparklines. El backend
    // devuelve lo que tenga; si arrancó hace poco vendrá con menos muestras.
    @GET("admin/server/host-history")
    suspend fun hostHistory(): com.musarum.mobile.data.network.dto.HostHistoryDto

    // Progreso del "Re-enriquecer todo" para la barra en Mantenimiento.
    // Es barato (solo lee contadores atómicos), se puede hacer polling corto.
    @GET("admin/server/enrich-progress")
    suspend fun enrichProgress(): com.musarum.mobile.data.network.dto.EnrichProgressDto

    // Cancela el "Re-enriquecer todo" en curso. Las tareas @Async en el
    // backend chequean la bandera al empezar y salen sin tocar Emendatium.
    @retrofit2.http.POST("admin/server/cancel-enrichment")
    suspend fun cancelEnrichment(): retrofit2.Response<Unit>

    @GET("version")
    suspend fun appVersion(): com.musarum.mobile.data.network.dto.VersionDto

    // ── Anuncio de versión (OWNER) ──
    @GET("admin/version")
    suspend fun currentPublishedUpdate():
            com.musarum.mobile.data.network.dto.AppUpdateDto

    @POST("admin/version")
    suspend fun publishUpdate(
        @Body request: com.musarum.mobile.data.network.dto.PublishUpdateRequest
    ): com.musarum.mobile.data.network.dto.AppUpdateDto

    @retrofit2.http.POST("admin/server/clean-orphan-lyrics")
    suspend fun cleanOrphanLyrics(): com.musarum.mobile.data.network.dto.CleanOrphanLyricsDto

    // Vacía el catálogo musical del servidor (canciones + artistas + álbumes +
    // géneros + referencias). Devuelve cuántos había antes del wipe.
    @retrofit2.http.POST("admin/server/wipe-library")
    suspend fun wipeLibrary(): com.musarum.mobile.data.network.dto.WipeLibrarySummaryDto

    // Re-lanza enriquecimiento Emendatium sobre todo el catálogo. Los datos
    // nuevos aparecen conforme Emendatium responde (fire-and-forget).
    @retrofit2.http.POST("admin/server/re-enrich-all")
    suspend fun reEnrichAll(): com.musarum.mobile.data.network.dto.ReEnrichSummaryDto

    // Barre artistas / álbumes / géneros que quedaron sin canciones.
    @retrofit2.http.POST("admin/server/clean-orphan-entities")
    suspend fun cleanOrphanEntities():
            com.musarum.mobile.data.network.dto.CleanOrphansSummaryDto

    // ── Carpetas de biblioteca (solo OWNER) ──
    @GET("admin/folders")
    suspend fun listLibraryFolders(): List<com.musarum.mobile.data.network.dto.LibraryFolderDto>

    @GET("admin/folders/available")
    suspend fun listDiscoverableFolders():
            List<com.musarum.mobile.data.network.dto.DiscoveredFolderDto>

    @POST("admin/folders")
    suspend fun addLibraryFolder(
        @Body request: com.musarum.mobile.data.network.dto.AddFolderRequest
    ): com.musarum.mobile.data.network.dto.LibraryFolderDto

    @retrofit2.http.PATCH("admin/folders/{id}")
    suspend fun toggleLibraryFolder(
        @Path("id") id: Long,
        @Query("active") active: Boolean
    ): com.musarum.mobile.data.network.dto.LibraryFolderDto

    @DELETE("admin/folders/{id}")
    suspend fun deleteLibraryFolder(@Path("id") id: Long)

    // ── Géneros ──

    @GET("genres/{id}/songs")
    suspend fun getGenreSongs(
        @Path("id") id: Long,
        @Query("page") page: Int = 0,
        @Query("size") size: Int = 500,
        @Query("sort") sort: String = "title,asc"
    ): PageDto<SongSummaryDto>

    // ── Home — convivencia ──

    @GET("home/presence")
    suspend fun getHomePresence(): List<com.musarum.mobile.data.network.dto.NowPlayingUserDto>

    @GET("home/discoveries")
    suspend fun getHomeDiscoveries(
        @Query("windowDays") windowDays: Int = 30,
        @Query("size") size: Int = 12
    ): com.musarum.mobile.data.network.dto.DiscoveryPageDto

    @GET("home/taste-profiles")
    suspend fun getHomeTasteProfiles(): List<com.musarum.mobile.data.network.dto.TasteProfileDto>

    @GET("home/smart")
    suspend fun getSmartPlaylists():
            List<com.musarum.mobile.data.network.dto.SmartPlaylistInfoDto>

    @GET("home/smart/{slug}")
    suspend fun getSmartPlaylistSongs(
        @Path("slug") slug: String,
        @Query("size") size: Int = 30
    ): List<SongSummaryDto>
}

