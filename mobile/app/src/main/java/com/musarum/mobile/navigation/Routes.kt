package com.musarum.mobile.navigation

object Routes {
    const val LOGIN = "login"
    const val HOME = "home"
    const val SONGS = "songs"
    const val ALBUMS = "albums"
    const val ARTISTS = "artists"
    const val SEARCH = "search"
    const val NOW_PLAYING = "now_playing"
    const val LYRICS = "lyrics"
    const val INFO = "song_info"
    const val VIDEO = "song_video"
    const val QUEUE = "queue"
    const val FAVORITES = "favorites"
    const val DOWNLOADS = "downloads"
    const val LIBRARY = "library"
    const val PROFILE = "profile"
    const val LIBRARY_FOLDERS = "profile/folders"
    const val USERS_ADMIN = "profile/users"
    const val SERVER_STATS = "profile/server"
    const val EQUALIZER = "profile/equalizer"

    const val PLAYLIST_DETAIL = "playlist/{id}"
    fun playlistDetail(id: Long) = "playlist/$id"

    const val ALBUM_DETAIL = "album/{id}"
    const val ARTIST_DETAIL = "artist/{id}"
    const val GENRE_DETAIL = "genre/{id}"

    fun albumDetail(id: Long) = "album/$id"
    fun artistDetail(id: Long) = "artist/$id"
    fun genreDetail(id: Long) = "genre/$id"
}
