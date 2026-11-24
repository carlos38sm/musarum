-- V10: portada propia por canción.
-- Flag binario: si TRUE, el móvil pide /songs/{id}/cover (y el disco
-- debe tener _covers/song-{id}.{ext}). Si FALSE, cae al cover del álbum.
-- Lo gestiona POST /songs/{id}/cover (OWNER) desde el móvil.
ALTER TABLE songs
    ADD COLUMN IF NOT EXISTS has_custom_cover BOOLEAN NOT NULL DEFAULT FALSE;
