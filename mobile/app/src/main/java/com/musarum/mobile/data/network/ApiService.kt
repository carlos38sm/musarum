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
}

