-- Handoffs de reproducción entre dispositivos del mismo backend. Un usuario
-- publica su "estado actual" (canción + posición + instante) y cualquier
-- otro dispositivo puede "tomar el relevo" para continuar en otro móvil.
-- Como es un registro por user, usamos UNIQUE en user_id y UPSERT.

CREATE TABLE IF NOT EXISTS playback_handoffs (
    user_id     BIGINT PRIMARY KEY,
    song_id     BIGINT NOT NULL,
    position_ms BIGINT NOT NULL,
    device_label VARCHAR(80),
    updated_at  TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    CONSTRAINT playback_handoffs_user_fk FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT playback_handoffs_song_fk FOREIGN KEY (song_id) REFERENCES songs (id)
);
