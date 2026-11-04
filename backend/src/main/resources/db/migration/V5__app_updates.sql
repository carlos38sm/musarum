-- V5: tabla singleton para publicar la última versión del APK desde el móvil
-- (en vez de tocar el .env del servidor y reiniciar el backend).
--
-- Solo existe UNA fila, id = 1, creada aquí con valores vacíos para que
-- VersionController siempre encuentre algo que leer. El POST /admin/version
-- hace UPDATE sobre esta misma fila; nunca INSERT, nunca DELETE.
CREATE TABLE app_updates (
    id            BIGINT      PRIMARY KEY,
    latest_apk    VARCHAR(32),
    apk_url       VARCHAR(512),
    mandatory     BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by    BIGINT      REFERENCES users(id) ON DELETE SET NULL
);

INSERT INTO app_updates (id, latest_apk, apk_url, mandatory)
VALUES (1, NULL, NULL, FALSE);
