-- V18: resetea cualquier image_url que NO sea http(s)://.
-- V15 (nov-24 2026) solo limpió URLs con patrón "/artists/%/image" del
-- scanner viejo. Si por lo que fuera quedaron otros patrones locales
-- (p. ej. "/albums/N/cover" guardado por error, o cualquier ruta relativa
-- sin dominio), V15 no los barrió y seguían pintando caras de álbum en
-- las bolitas de artista.
--
-- A partir del bloque "fotos reales de artista (nov-26 2026)", Emendatium
-- rellena image_url con la URL directa de TheAudioDB (strArtistThumb /
-- strArtistLogo), siempre http(s)://. Así que cualquier valor que NO
-- empiece por http:// o https:// es un residuo histórico y lo
-- reseteamos a NULL — el móvil pintará placeholder determinista hasta
-- que "Re-enriquecer todo" traiga la foto real.
UPDATE artists
    SET image_url = NULL
    WHERE image_url IS NOT NULL
      AND image_url NOT LIKE 'http://%'
      AND image_url NOT LIKE 'https://%';
