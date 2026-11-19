-- Añade última conexión al perfil de usuario.
-- Se actualiza en cada login exitoso (AuthService.authenticate). Se expone
-- solo al OWNER en el panel de Usuarios para ver quién llevaba sin conectarse
-- más tiempo (útil cuando creces a varios miembros y quieres saber quién sigue
-- activo). No pretende ser "última actividad" exhaustiva: un user que mantiene
-- la app abierta sin cerrar sesión no la actualiza.

ALTER TABLE users
    ADD COLUMN last_login_at TIMESTAMP WITH TIME ZONE NULL;
