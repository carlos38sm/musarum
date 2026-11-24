-- Índices de lookup por clave normalizada (name_key / title_key).
--
-- ScannerCatalog carga los pares (name → id) en memoria antes del scan,
-- así que durante el bulk import no se nota. Pero el LibraryWatcher
-- (ficheros añadidos EN CALIENTE) y los endpoints findOrCreate directos
-- SÍ hacen SELECT por name_key / title_key sin índice → seq scan sobre
-- la tabla de artists/albums. Con 1.500+ artistas y 4.000+ álbumes empieza
-- a doler.
--
-- IF NOT EXISTS por si alguien ya los creó manualmente desde psql.

CREATE INDEX IF NOT EXISTS idx_artists_name_key  ON artists (name_key);
CREATE INDEX IF NOT EXISTS idx_albums_title_key  ON albums  (title_key);
