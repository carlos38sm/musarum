package emendatium.backend.sources.lrclib;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Cliente de LRCLib con múltiples estrategias de matching.
 *
 * Filosofía: LRCLib es muy estricto con nombres exactos. Muchas canciones
 * famosas están indexadas con variantes ligeramente distintas a lo que
 * devuelve MusicBrainz (comillas curvas, "feat.", "(Remaster)", etc.).
 *
 * Estrategias en cascada:
 *   1. GET exacto con nombre normalizado (típico match rápido)
 *   2. GET exacto sin álbum (por si el álbum difiere)
 *   3. SEARCH tolerante con nombre normalizado
 *   4. SEARCH con título "limpio" (sin (feat.), (Remaster), etc.)
 *   5. SEARCH con título "stripped" (sin apóstrofos ni signos)
 *
 * En cada resultado, aplicamos VERIFICACIÓN de artista para evitar
 * meter una letra de otro artista con el mismo título.
 */
@Slf4j
@Component
public class LrcLibClient {

    private static final String GET_URL    = "https://lrclib.net/api/get";
    private static final String SEARCH_URL = "https://lrclib.net/api/search";

    private final RestClient http;

    public LrcLibClient(RestClient enricherRestClient) {
        this.http = enricherRestClient;
    }

    public Optional<LrcLibResponse> fetch(String artist, String title, String album, Integer durationSec) {
        log.info("📡 LrcLibClient.fetch()");
        log.info("    → artist: '{}'", artist);
        log.info("    → title:  '{}'", title);
        log.info("    → album:  '{}'", album);
        log.info("    → duration: {}", durationSec);

        if (artist == null || title == null) {
            log.info("    → SALIENDO: artist o title es null");
            return Optional.empty();
        }

        // Normalizamos los tres campos de partida
        String artistN = normalizeTypography(artist);
        String titleN  = normalizeTypography(title);
        String albumN  = normalizeTypography(album);

        // Estrategia 1: GET con todo normalizado
        log.info("    🔹 Estrategia 1: /api/get con todo normalizado");
        Optional<LrcLibResponse> r1 = tryGet(artistN, titleN, albumN, durationSec);
        if (usable(r1) && verifyArtist(r1.get(), artistN)) {
            log.info("    ✓ MATCH estrategia 1 (get exacto)");
            return r1;
        }

        // Estrategia 2: GET sin álbum (por si difiere)
        if (albumN != null && !albumN.isBlank()) {
            log.info("    🔹 Estrategia 2: /api/get sin álbum");
            Optional<LrcLibResponse> r2 = tryGet(artistN, titleN, null, durationSec);
            if (usable(r2) && verifyArtist(r2.get(), artistN)) {
                log.info("    ✓ MATCH estrategia 2 (get sin álbum)");
                return r2;
            }
        }

        // Estrategia 3: SEARCH normalizado
        log.info("    🔹 Estrategia 3: /api/search normalizado");
        Optional<LrcLibResponse> r3 = trySearch(artistN, titleN, durationSec, artistN);
        if (usable(r3) && verifyArtist(r3.get(), artistN)) {
            log.info("    ✓ MATCH estrategia 3 (search normalizado)");
            return r3;
        }

        // Estrategia 4: SEARCH con título limpio (sin "feat.", "(Remaster)", etc.)
        String titleCleaned = cleanTitle(titleN);
        if (!titleCleaned.equalsIgnoreCase(titleN)) {
            log.info("    🔹 Estrategia 4: /api/search título limpio: '{}'", titleCleaned);
            Optional<LrcLibResponse> r4 = trySearch(artistN, titleCleaned, durationSec, artistN);
            if (usable(r4) && verifyArtist(r4.get(), artistN)) {
                log.info("    ✓ MATCH estrategia 4 (título limpio)");
                return r4;
            }
        }

        // Estrategia 5: SEARCH con título stripped (sin apóstrofos ni signos)
        String titleStripped = stripSpecialChars(titleCleaned);
        if (!titleStripped.equalsIgnoreCase(titleCleaned)) {
            log.info("    🔹 Estrategia 5: /api/search título stripped: '{}'", titleStripped);
            Optional<LrcLibResponse> r5 = trySearch(artistN, titleStripped, durationSec, artistN);
            if (usable(r5) && verifyArtist(r5.get(), artistN)) {
                log.info("    ✓ MATCH estrategia 5 (título stripped)");
                return r5;
            }
        }

        log.info("    ✗ LrcLib NO tiene letra fiable para: {} - {}", artist, title);
        return Optional.empty();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  LLAMADAS A LA API
    // ═══════════════════════════════════════════════════════════════════

    private Optional<LrcLibResponse> tryGet(String artist, String title, String album, Integer duration) {
        try {
            StringBuilder url = new StringBuilder(GET_URL)
                    .append("?artist_name=").append(encode(artist))
                    .append("&track_name=").append(encode(title));
            if (album != null && !album.isBlank()) url.append("&album_name=").append(encode(album));
            if (duration != null && duration > 0) url.append("&duration=").append(duration);
            log.info("        GET {}", url);

            LrcLibResponse resp = http.get().uri(url.toString()).retrieve().body(LrcLibResponse.class);
            if (resp != null) {
                log.info("        → id={}, artist='{}', syncedLen={}, plainLen={}",
                        resp.id(), resp.artistName(),
                        resp.syncedLyrics() != null ? resp.syncedLyrics().length() : 0,
                        resp.plainLyrics() != null ? resp.plainLyrics().length() : 0);
            }
            return Optional.ofNullable(resp);
        } catch (Exception e) {
            log.info("        → {} ({})", e.getClass().getSimpleName(), extractShort(e));
            return Optional.empty();
        }
    }

    /**
     * SEARCH devuelve un array. Elegimos el mejor:
     *   1. Filtrar los que NO son del artista correcto (previene suplantación)
     *   2. Preferir los que tienen syncedLyrics
     *   3. Preferir el que tiene duración más cercana
     */
    private Optional<LrcLibResponse> trySearch(String artist, String title, Integer duration, String artistForVerify) {
        try {
            String url = SEARCH_URL + "?artist_name=" + encode(artist) + "&track_name=" + encode(title);
            log.info("        SEARCH {}", url);

            LrcLibResponse[] results = http.get().uri(url).retrieve().body(LrcLibResponse[].class);
            if (results == null || results.length == 0) {
                log.info("        → 0 resultados");
                return Optional.empty();
            }
            log.info("        → {} resultados", results.length);

            // Filtrar por artista antes de nada — evita meter letras de covers/otros
            List<LrcLibResponse> ownedByArtist = new ArrayList<>();
            for (LrcLibResponse r : results) {
                if (r.artistName() != null && artistsRoughlyEqual(r.artistName(), artistForVerify)) {
                    ownedByArtist.add(r);
                }
            }
            log.info("        → {} candidatos con artista coincidente", ownedByArtist.size());

            if (ownedByArtist.isEmpty()) {
                log.info("        → NINGÚN candidato tiene el artista correcto: descartamos");
                return Optional.empty();
            }

            // Preferimos synced con duración cercana
            LrcLibResponse best = null;
            int bestDiff = Integer.MAX_VALUE;
            for (LrcLibResponse r : ownedByArtist) {
                if (r.syncedLyrics() == null || r.syncedLyrics().isBlank()) continue;
                int diff = (duration != null && r.duration() != null) ? Math.abs(r.duration() - duration) : 0;
                if (diff < bestDiff) { bestDiff = diff; best = r; }
            }

            // Si nadie tiene synced, tomamos el mejor con plain
            if (best == null) {
                for (LrcLibResponse r : ownedByArtist) {
                    if (r.plainLyrics() != null && !r.plainLyrics().isBlank()) {
                        int diff = (duration != null && r.duration() != null) ? Math.abs(r.duration() - duration) : 0;
                        if (diff < bestDiff) { bestDiff = diff; best = r; }
                    }
                }
            }

            if (best != null) {
                log.info("        → elegido id={} (dur={}, diff={}, sync={})",
                        best.id(), best.duration(), bestDiff,
                        best.syncedLyrics() != null && !best.syncedLyrics().isBlank());
            }
            return Optional.ofNullable(best);
        } catch (Exception e) {
            log.info("        → {} ({})", e.getClass().getSimpleName(), extractShort(e));
            return Optional.empty();
        }
    }

    // ═══════════════════════════════════════════════════════════════════
    //  VERIFICACIÓN DE ARTISTA (anti-suplantación)
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Antes de aceptar una letra de LrcLib, verificamos que el nombre del
     * artista devuelto por LrcLib coincida (de forma tolerante) con el que
     * buscábamos. Así evitamos meter la letra de un cover o de otro artista.
     */
    private boolean verifyArtist(LrcLibResponse resp, String expectedArtist) {
        if (resp == null || resp.artistName() == null || resp.artistName().isBlank()) {
            // Sin artista en el resultado NO podemos verificar nada. Preferimos
            // rechazar y seguir con la siguiente estrategia: aceptar a ciegas
            // es la forma más común de meter letras equivocadas.
            log.info("    ⚠ Verificación imposible: artistName vacío → RECHAZADO");
            return false;
        }
        boolean ok = artistsRoughlyEqual(resp.artistName(), expectedArtist);
        log.info("    🔒 Verificación: LrcLib='{}' vs esperado='{}' → {}",
                resp.artistName(), expectedArtist, ok ? "OK" : "RECHAZADO");
        return ok;
    }

    /**
     * Compara dos nombres de artista tolerantemente:
     *   - Ignora mayúsculas y acentos
     *   - Ignora "The " inicial
     *   - Trata comas y "&" como equivalentes (Beyoncé & Jay-Z ≈ Beyoncé, Jay-Z)
     *   - Considera coincidencia si el principal es sustring del otro
     *     (para "Katy Perry" vs "Katy Perry, Juicy J")
     */
    private boolean artistsRoughlyEqual(String a, String b) {
        if (a == null || b == null) return false;
        String na = simplifyArtist(a);
        String nb = simplifyArtist(b);
        if (na.equals(nb)) return true;
        if (na.contains(nb) || nb.contains(na)) return true;
        // También si comparten el primer artista listado
        String firstA = firstArtistOnly(na);
        String firstB = firstArtistOnly(nb);
        return firstA.equals(firstB);
    }

    private String simplifyArtist(String s) {
        String n = Normalizer.normalize(s.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        // "the beatles" -> "beatles"
        if (n.startsWith("the ")) n = n.substring(4);
        // Uniformar separadores
        n = n.replaceAll("\\s*(feat\\.?|ft\\.?|featuring|with)\\s+", ", ");
        n = n.replaceAll("\\s*(&|\\+|/|x)\\s*", ", ");
        return n.trim();
    }

    private String firstArtistOnly(String simplified) {
        int c = simplified.indexOf(',');
        return c > 0 ? simplified.substring(0, c).trim() : simplified;
    }

    // ═══════════════════════════════════════════════════════════════════
    //  NORMALIZACIÓN DE CADENAS
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Convierte comillas y guiones tipográficos a sus equivalentes ASCII.
     * MusicBrainz suele devolver "Don't" con U+2019, pero LrcLib indexa
     * con U+0027 (apóstrofo normal).
     */
    private String normalizeTypography(String s) {
        if (s == null) return null;
        return s
                .replace('\u2018', '\'')  // ' → '
                .replace('\u2019', '\'')  // ' → '
                .replace('\u201C', '"')   // " → "
                .replace('\u201D', '"')   // " → "
                .replace('\u2013', '-')   // – → -
                .replace('\u2014', '-')   // — → -
                .replace('\u00B4', '\'')  // ´ → '
                .replace('\u0060', '\''); // ` → '
    }

    /**
     * Quita basura común de títulos que LrcLib no suele tener indexada:
     *   - "(feat. XYZ)" / "[ft. XYZ]"
     *   - "(Remastered)" / "(Remaster 2019)" / "(2019 Remaster)"
     *   - "(Radio Edit)" / "(Album Version)" / "(Original Mix)"
     *   - "(Live)" / "(Acoustic)" / "(Explicit)"
     *   - "(Main Version)" / "(Main Radio Edit)"
     */
    private String cleanTitle(String s) {
        if (s == null) return null;
        String r = s;

        // Feat/ft con paréntesis o corchetes
        r = r.replaceAll("(?i)\\s*[\\(\\[]\\s*(feat\\.?|ft\\.?|featuring|with)\\s+[^\\)\\]]*[\\)\\]]", "");
        // Feat sin paréntesis (al final del título)
        r = r.replaceAll("(?i)\\s+(feat\\.?|ft\\.?|featuring|with)\\s+.+$", "");
        // Remaster/Remix/Radio Edit/Version en paréntesis o corchetes
        r = r.replaceAll("(?i)\\s*[\\(\\[][^\\)\\]]*(remaster|remix|radio edit|album version|original mix|live|acoustic|explicit|clean|main version|main radio edit|extended|instrumental|deluxe|bonus)[^\\)\\]]*[\\)\\]]", "");
        // Sufijos año remaster: "Song - 2019 Remaster"
        r = r.replaceAll("(?i)\\s*-\\s*\\d{4}\\s+(remaster|remastered|version).*$", "");
        // Sufijos genéricos: "Song - Radio Edit"
        r = r.replaceAll("(?i)\\s*-\\s*(radio edit|album version|original mix|live|acoustic|explicit|clean|extended|instrumental).*$", "");

        return r.trim();
    }

    /**
     * Último recurso: quita apóstrofos, comillas y signos por si LrcLib
     * indexó el título sin ellos (ej: "Dont Stop the Music").
     */
    private String stripSpecialChars(String s) {
        if (s == null) return null;
        return s
                .replace("'", "")
                .replace("\"", "")
                .replace("?", "")
                .replace("!", "")
                .replace(",", "")
                .replace(".", "")
                .replaceAll("\\s+", " ")
                .trim();
    }

    // ═══════════════════════════════════════════════════════════════════
    //  UTILIDADES
    // ═══════════════════════════════════════════════════════════════════

    private boolean usable(Optional<LrcLibResponse> o) {
        if (o.isEmpty()) return false;
        LrcLibResponse r = o.get();
        return (r.syncedLyrics() != null && !r.syncedLyrics().isBlank())
                || (r.plainLyrics() != null && !r.plainLyrics().isBlank());
    }

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String extractShort(Exception e) {
        String msg = e.getMessage();
        if (msg == null) return "";
        return msg.length() > 80 ? msg.substring(0, 80) + "..." : msg;
    }
}