-- Tracking de enriquecimiento Emendatium por canción.
--   enriched_at    : momento del último intento (éxito o fallo).
--   enrich_status  : NULL = nunca intentada,
--                    'OK' = enriquecida (al menos un campo poblado),
--                    'FAILED_NO_MATCH' = intentada, Emendatium no reconoció
--                        la huella / no hubo match en MusicBrainz → no sirve
--                        reintentar automáticamente,
--                    'FAILED_TRANSIENT' = fallo temporal (timeout, red, 5xx)
--                        → reintentable.
--
-- El botón "Re-enriquecer todo" sólo procesa NULL y FAILED_TRANSIENT, ahorra
-- trabajo en canciones que ya sabemos que Emendatium no puede identificar.
ALTER TABLE songs ADD COLUMN enriched_at TIMESTAMP;
ALTER TABLE songs ADD COLUMN enrich_status VARCHAR(32);

-- Índice parcial: la mayoría de canciones ya enriquecidas no se consultan
-- por status, sólo las FAILED_TRANSIENT/NULL cuando el OWNER re-enriquece.
CREATE INDEX idx_songs_enrich_status
    ON songs(enrich_status)
    WHERE enrich_status IS NOT NULL;
