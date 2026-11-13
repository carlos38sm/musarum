-- Toggles de privacidad para la vista "Home — convivencia":
--   share_now_playing : si false, no aparezco en "En Musarum ahora" para los
--       demás (otros usuarios ven lo que yo estoy escuchando en vivo).
--   share_taste       : si false, no aparezco en "Mini-perfiles" (otros ven
--       mi top artista / canción / género de la semana).
--
-- Ambos por defecto TRUE (convivencia es el espíritu de la app para 4 users
-- en Tailscale; quien quiera invisibilidad lo apaga).
ALTER TABLE users ADD COLUMN share_now_playing BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE users ADD COLUMN share_taste BOOLEAN NOT NULL DEFAULT TRUE;
