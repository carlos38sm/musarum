-- Índices para cubrir las FK y queries que hoy caen en table scan.
-- Postgres NO crea índice automático sobre FOREIGN KEY (solo sobre PK/UNIQUE),
-- así que cada JOIN o WHERE sobre una FK recorre toda la tabla salvo que haya
-- un índice explícito. Con ~549 canciones de arranque y ~7000 objetivo esto
-- ya empieza a notarse en:
--  - PlaylistDetail / FavoritesScreen (borrar canción recorre favorites por song_id)
--  - AlbumDetail (findByAlbumIdOrderByTrackNumberAsc)
--  - GenreDetail (findByGenreId)
--  - Home "olvidadas" / Server stats (reproductions by song_id)
--  - Taste profile "top artist" (GROUP BY song_artists.artist_id)
--
-- IF NOT EXISTS por si alguna base ya los había creado a mano desde psql.

CREATE INDEX IF NOT EXISTS idx_songs_album_id      ON songs (album_id);
CREATE INDEX IF NOT EXISTS idx_songs_genre_id      ON songs (genre_id);
CREATE INDEX IF NOT EXISTS idx_songs_created_at    ON songs (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_favorites_song_id   ON favorites (song_id);
CREATE INDEX IF NOT EXISTS idx_playlist_entries_song_id ON playlist_entries (song_id);
CREATE INDEX IF NOT EXISTS idx_playlist_entries_playlist_id_position
    ON playlist_entries (playlist_id, "position");

CREATE INDEX IF NOT EXISTS idx_reproductions_song_id ON reproductions (song_id);

-- song_artists.artist_id: el PK es (song_id, artist_id). El prefijo cubre
-- lookups por song_id, pero GROUP BY artist_id (home "top artista" en 7 días)
-- cae en seq scan. Índice sobre artist_id lo arregla.
CREATE INDEX IF NOT EXISTS idx_song_artists_artist_id ON song_artists (artist_id);

-- Playlists por owner: lista "mis playlists" del usuario.
CREATE INDEX IF NOT EXISTS idx_playlists_owner_id ON playlists (owner_id);
