-- ============================================================================
--  V2 — deduplicar songs por audio_path y añadir UNIQUE
-- ============================================================================
--  Contexto: al cambiar el formato de content_hash (SHA-256 completo →
--  SHA-256 del primer 1 MB + ':' + size), un re-escaneo trató las canciones
--  existentes como nuevas e insertó filas con el mismo audio_path pero
--  distinto content_hash. En V1 solo content_hash era UNIQUE, así que
--  nada paró el duplicado.
--
--  Esta migración:
--    1. Para cada audio_path repetido, se queda con la fila de MENOR id
--       (la más antigua, que es la que conserva favoritos, playlists,
--       historial, etc.). Las demás se borran, pero antes se re-asignan
--       sus favoritos / entradas de playlist / reproducciones a la fila
--       canónica para no romper las FK.
--    2. Añade UNIQUE sobre audio_path para prevenir que vuelva a pasar.
-- ============================================================================

-- 1a. Re-asignar favoritos de las duplicadas a la canción canónica (min id
--     por audio_path). Un favorito ya migrado puede chocar con uno existente
--     en la canónica → DELETE antes del UPDATE para evitar violar la UK
--     (user_id, song_id).
WITH canonical AS (
    SELECT audio_path, MIN(id) AS canonical_id
    FROM songs
    GROUP BY audio_path
    HAVING COUNT(*) > 1
),
duplicated_songs AS (
    SELECT s.id AS dup_id, c.canonical_id
    FROM songs s
    JOIN canonical c ON s.audio_path = c.audio_path AND s.id <> c.canonical_id
)
DELETE FROM favorites f
USING duplicated_songs d
WHERE f.song_id = d.dup_id
  AND EXISTS (
      SELECT 1 FROM favorites f2
      WHERE f2.song_id = d.canonical_id AND f2.user_id = f.user_id
  );

WITH canonical AS (
    SELECT audio_path, MIN(id) AS canonical_id
    FROM songs
    GROUP BY audio_path
    HAVING COUNT(*) > 1
),
duplicated_songs AS (
    SELECT s.id AS dup_id, c.canonical_id
    FROM songs s
    JOIN canonical c ON s.audio_path = c.audio_path AND s.id <> c.canonical_id
)
UPDATE favorites f
SET song_id = d.canonical_id
FROM duplicated_songs d
WHERE f.song_id = d.dup_id;

-- 1b. Entradas de playlist: igual que favoritos pero con UK (playlist_id, song_id).
WITH canonical AS (
    SELECT audio_path, MIN(id) AS canonical_id
    FROM songs
    GROUP BY audio_path
    HAVING COUNT(*) > 1
),
duplicated_songs AS (
    SELECT s.id AS dup_id, c.canonical_id
    FROM songs s
    JOIN canonical c ON s.audio_path = c.audio_path AND s.id <> c.canonical_id
)
DELETE FROM playlist_entries pe
USING duplicated_songs d
WHERE pe.song_id = d.dup_id
  AND EXISTS (
      SELECT 1 FROM playlist_entries pe2
      WHERE pe2.song_id = d.canonical_id AND pe2.playlist_id = pe.playlist_id
  );

WITH canonical AS (
    SELECT audio_path, MIN(id) AS canonical_id
    FROM songs
    GROUP BY audio_path
    HAVING COUNT(*) > 1
),
duplicated_songs AS (
    SELECT s.id AS dup_id, c.canonical_id
    FROM songs s
    JOIN canonical c ON s.audio_path = c.audio_path AND s.id <> c.canonical_id
)
UPDATE playlist_entries pe
SET song_id = d.canonical_id
FROM duplicated_songs d
WHERE pe.song_id = d.dup_id;

-- 1c. Reproducciones: no tienen UK sobre (user_id, song_id) — podemos
--     reasignar en masa sin borrar nada; el historial queda consolidado.
WITH canonical AS (
    SELECT audio_path, MIN(id) AS canonical_id
    FROM songs
    GROUP BY audio_path
    HAVING COUNT(*) > 1
),
duplicated_songs AS (
    SELECT s.id AS dup_id, c.canonical_id
    FROM songs s
    JOIN canonical c ON s.audio_path = c.audio_path AND s.id <> c.canonical_id
)
UPDATE reproductions r
SET song_id = d.canonical_id
FROM duplicated_songs d
WHERE r.song_id = d.dup_id;

-- 1d. Relación N:M song_artists: borrar las filas de las duplicadas; la
--     canónica ya tiene sus propios artistas asignados (son los mismos).
WITH canonical AS (
    SELECT audio_path, MIN(id) AS canonical_id
    FROM songs
    GROUP BY audio_path
    HAVING COUNT(*) > 1
)
DELETE FROM song_artists sa
WHERE sa.song_id IN (
    SELECT s.id FROM songs s
    JOIN canonical c ON s.audio_path = c.audio_path
    WHERE s.id <> c.canonical_id
);

-- 1e. Al fin, borrar las filas duplicadas de songs.
WITH canonical AS (
    SELECT audio_path, MIN(id) AS canonical_id
    FROM songs
    GROUP BY audio_path
    HAVING COUNT(*) > 1
)
DELETE FROM songs s
USING canonical c
WHERE s.audio_path = c.audio_path
  AND s.id <> c.canonical_id;

-- 2. UNIQUE sobre audio_path. A partir de aquí, cualquier intento de
--    insertar una canción con un path ya existente fallará en el ORM.
ALTER TABLE songs
    ADD CONSTRAINT songs_audio_path_unique UNIQUE (audio_path);
