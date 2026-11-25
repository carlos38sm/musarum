-- Índices complementarios para el feed de "Comunidad" y métricas globales
-- del Home. V8 ya cubrió las FK y los casos per-user; estos cubren los
-- lookups GLOBALES por timestamp que aún caían en seq scan.
--
-- 1. favorites(created_at): HomeService.activity hace `findRecentSince` →
--    "WHERE created_at > :since ORDER BY created_at DESC LIMIT N". Sin este
--    índice ordenaba toda la tabla.
--
-- 2. reproductions(created_at): el panel OWNER pide countSince(7d/30d) y
--    MAX(createdAt) global. V1 tiene (user_id, created_at) pero no un índice
--    solo por created_at — las agregadas globales no podían usarlo.

CREATE INDEX IF NOT EXISTS idx_favorites_created_at
    ON favorites (created_at DESC);

CREATE INDEX IF NOT EXISTS idx_reproductions_created_at
    ON reproductions (created_at DESC);
