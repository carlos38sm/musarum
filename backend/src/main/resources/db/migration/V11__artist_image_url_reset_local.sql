-- V11: resetea las imágenes de artista que apuntan al endpoint local
-- /artists/{id}/image. Hasta nov-24 2026 el LibraryScanner guardaba la
-- portada EMBEBIDA en el audio (que es del álbum) como "cara" del artista
-- si éste no tenía ninguna. Resultado: casi todos los artistas tenían
-- como foto la carátula de un álbum.
--
-- Después del fix en el scanner, las nuevas canciones ya NO hacen eso.
-- Esta migración limpia lo heredado, dejando a NULL las URLs locales:
-- el móvil pintará un placeholder determinista por artistId hasta que
-- Emendatium (u otro origen externo) nos dé la foto real.
--
-- Importante: SOLO toca las URLs que apuntan al endpoint /artists/*/image.
-- Si un artista tiene una URL http(s)://… (p. ej. porque un futuro
-- Emendatium cargue imágenes externas), queda intacta.
UPDATE artists
    SET image_url = NULL
    WHERE image_url LIKE '/artists/%/image';
