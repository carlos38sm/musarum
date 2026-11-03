-- ============================================================================
--  V4 — avatar de usuario y estado "bloqueado"
-- ============================================================================
--  avatar_url: URL relativa que el móvil usa para pedir la imagen de perfil.
--              Convención: '/users/{id}/avatar' (igual estilo que covers de
--              album). null = sin foto, se pinta inicial del nick como hasta
--              ahora.
--  blocked:    si true, el login rechaza con 401 "cuenta bloqueada" aunque
--              las credenciales sean correctas. Pensado para que el OWNER
--              pueda "silenciar" temporalmente a un oyente sin borrar su
--              historial, favoritos ni playlists.
-- ============================================================================

ALTER TABLE users
    ADD COLUMN avatar_url VARCHAR(255),
    ADD COLUMN blocked    BOOLEAN NOT NULL DEFAULT FALSE;
