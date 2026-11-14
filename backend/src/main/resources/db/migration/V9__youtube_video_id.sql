-- Cache del videoId de YouTube asociado a cada canción. El backend hace
-- scraping ligero de youtube.com/results la primera vez que alguien pide
-- "ver vídeo" en el móvil y guarda el videoId aquí; las siguientes visitas
-- son instantáneas y sin tocar YouTube. El `yt_fetched_at` sirve para
-- invalidar la caché manualmente si el scraping deja de dar resultados
-- válidos (p. ej. YouTube cambia su HTML).

ALTER TABLE songs ADD COLUMN IF NOT EXISTS youtube_video_id VARCHAR(16);
ALTER TABLE songs ADD COLUMN IF NOT EXISTS yt_fetched_at   TIMESTAMP(6) WITH TIME ZONE;
