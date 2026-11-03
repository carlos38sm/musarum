package musarum.backend.enrichment;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Cliente directo a LrcLib.net para letras sincronizadas en caliente.
 *
 * Rediseñado nov 2026 copiando la versión robusta de Emendatium: 5 estrategias
 * en cascada + verificación de artista en todas (anti-cover/suplantación) +
 * normalización tipográfica (apóstrofos curvos → ASCII, comillas, guiones…)
 * que es la razón principal por la que LrcLib "no encontraba" canciones muy
 * populares — MusicBrainz entrega "Don't" con U+2019 pero LrcLib indexa U+0027.
 *
 * Estrategias:
 *   1. GET exacto con todo normalizado
 *   2. GET exacto sin álbum (algunas difieren)
 *   3. SEARCH tolerante
 *   4. SEARCH con título "limpio" (sin (feat.), (Remaster)…)
 *   5. SEARCH con título "stripped" (sin apóstrofos/signos)
 */
@Component
public class LrcLibClient {

    private static final Logger log = LoggerFactory.getLogger(LrcLibClient.class);
    private static final String GET_URL    = "https://lrclib.net/api/get";
    private static final String SEARCH_URL = "https://lrclib.net/api/search";

    /** LrcLib exige User-Agent identificando la app (lo pedían explícito en su README). */
    private static final String USER_AGENT = "Musarum/1.0 ( https://github.com/carlossanchezmiranda/musarum )";

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT    = Duration.ofSeconds(4);

    private final RestClient http = RestClient.builder()
            .requestFactory(buildRequestFactory())
            .defaultHeader("User-Agent", USER_AGENT)
            .defaultHeader("Accept", "application/json")
            .build();

    private static JdkClientHttpRequestFactory buildRequestFactory() {
        HttpClient jdk = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(jdk);
        f.setReadTimeout(READ_TIMEOUT);
        return f;
    }

    /**
     * Compatibilidad con el callsite antiguo: busca letra para artist/title/duration
     * sin álbum. La variante con álbum vive en {@link #fetchLyrics}.
     */
    public Optional<String> fetchSyncedLyrics(String artist, String title, Integer durationSec) {
        return fetchLyrics(artist, title, null, durationSec);
    }

    public Optional<String> fetchLyrics(String artist, String title, String album, Integer durationSec) {
        if (artist == null || title == null || artist.isBlank() || title.isBlank()) {
            log.warn("LrcLib fetch con artist/title inválidos (artist={}, title={})", artist, title);
            return Optional.empty();
        }

        String artistN = normalizeTypography(artist);
        String titleN  = normalizeTypography(title);
        String albumN  = normalizeTypography(album);

        log.info("🔍 LrcLib fetch: '{}' - '{}' ({}s)", artistN, titleN, durationSec);

        // 1. GET exacto con todo normalizado
        Optional<LrcLibEntry> r1 = tryGet(artistN, titleN, albumN, durationSec);
        if (r1.isPresent() && usable(r1.get()) && verifyArtist(r1.get(), artistN)) {
            log.info("  ✓ Match estrategia 1 (GET exacto)");
            return pickLyrics(r1.get());
        }

        // 2. GET sin álbum
        if (albumN != null && !albumN.isBlank()) {
            Optional<LrcLibEntry> r2 = tryGet(artistN, titleN, null, durationSec);
            if (r2.isPresent() && usable(r2.get()) && verifyArtist(r2.get(), artistN)) {
                log.info("  ✓ Match estrategia 2 (GET sin álbum)");
                return pickLyrics(r2.get());
            }
        }

        // 3. SEARCH normalizado
        Optional<LrcLibEntry> r3 = trySearch(artistN, titleN, durationSec, artistN);
        if (r3.isPresent() && verifyArtist(r3.get(), artistN)) {
            log.info("  ✓ Match estrategia 3 (SEARCH normalizado)");
            return pickLyrics(r3.get());
        }

        // 4. SEARCH con título limpio
        String titleCleaned = cleanTitle(titleN);
        if (!titleCleaned.equalsIgnoreCase(titleN)) {
            Optional<LrcLibEntry> r4 = trySearch(artistN, titleCleaned, durationSec, artistN);
            if (r4.isPresent() && verifyArtist(r4.get(), artistN)) {
                log.info("  ✓ Match estrategia 4 (título limpio: '{}')", titleCleaned);
                return pickLyrics(r4.get());
            }
        }

        // 5. SEARCH con título stripped (sin apóstrofos/signos)
        String titleStripped = stripSpecialChars(titleCleaned);
        if (!titleStripped.equalsIgnoreCase(titleCleaned)) {
            Optional<LrcLibEntry> r5 = trySearch(artistN, titleStripped, durationSec, artistN);
            if (r5.isPresent() && verifyArtist(r5.get(), artistN)) {
                log.info("  ✓ Match estrategia 5 (stripped: '{}')", titleStripped);
                return pickLyrics(r5.get());
            }
        }

        log.warn("  ✗ LrcLib sin letra fiable para '{} - {}'", artist, title);
        return Optional.empty();
    }

    private Optional<LrcLibEntry> tryGet(String artist, String title, String album, Integer durationSec) {
        try {
            StringBuilder url = new StringBuilder(GET_URL)
                    .append("?artist_name=").append(enc(artist))
                    .append("&track_name=").append(enc(title));
            if (album != null && !album.isBlank()) url.append("&album_name=").append(enc(album));
            if (durationSec != null && durationSec > 0) url.append("&duration=").append(durationSec);
            log.info("  GET {}", url);
            LrcLibEntry resp = http.get().uri(java.net.URI.create(url.toString()))
                    .retrieve().body(LrcLibEntry.class);
            return Optional.ofNullable(resp);
        } catch (Exception e) {
            log.info("  GET falló: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private Optional<LrcLibEntry> trySearch(String artist, String title, Integer durationSec, String artistForVerify) {
        try {
            String url = SEARCH_URL + "?artist_name=" + enc(artist) + "&track_name=" + enc(title);
            log.info("  SEARCH {}", url);
            LrcLibEntry[] results = http.get().uri(java.net.URI.create(url))
                    .retrieve().body(LrcLibEntry[].class);
            if (results == null || results.length == 0) return Optional.empty();

            // Filtra por artista — evita covers y homónimos.
            List<LrcLibEntry> owned = new ArrayList<>();
            for (LrcLibEntry r : results) {
                if (r.artistName() != null && artistsRoughlyEqual(r.artistName(), artistForVerify)) {
                    owned.add(r);
                }
            }
            if (owned.isEmpty()) return Optional.empty();

            // Preferimos synced con duración más cercana; si ninguno tiene synced,
            // mejor plain con duración más cercana.
            LrcLibEntry best = null;
            int bestDiff = Integer.MAX_VALUE;
            for (LrcLibEntry r : owned) {
                if (r.syncedLyrics() == null || r.syncedLyrics().isBlank()) continue;
                int diff = (durationSec != null && r.duration() != null)
                        ? Math.abs(r.duration() - durationSec) : 0;
                if (diff < bestDiff) { bestDiff = diff; best = r; }
            }
            if (best == null) {
                for (LrcLibEntry r : owned) {
                    if (r.plainLyrics() == null || r.plainLyrics().isBlank()) continue;
                    int diff = (durationSec != null && r.duration() != null)
                            ? Math.abs(r.duration() - durationSec) : 0;
                    if (diff < bestDiff) { bestDiff = diff; best = r; }
                }
            }
            return Optional.ofNullable(best);
        } catch (Exception e) {
            log.info("  SEARCH falló: {}", e.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private boolean verifyArtist(LrcLibEntry resp, String expectedArtist) {
        if (resp == null || resp.artistName() == null || resp.artistName().isBlank()) {
            return false;
        }
        return artistsRoughlyEqual(resp.artistName(), expectedArtist);
    }

    private boolean artistsRoughlyEqual(String a, String b) {
        if (a == null || b == null) return false;
        String na = simplifyArtist(a);
        String nb = simplifyArtist(b);
        if (na.equals(nb)) return true;
        if (na.contains(nb) || nb.contains(na)) return true;
        String firstA = firstArtistOnly(na);
        String firstB = firstArtistOnly(nb);
        return firstA.equals(firstB);
    }

    private String simplifyArtist(String s) {
        String n = Normalizer.normalize(s.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        if (n.startsWith("the ")) n = n.substring(4);
        n = n.replaceAll("\\s*(feat\\.?|ft\\.?|featuring|with)\\s+", ", ");
        n = n.replaceAll("\\s*(&|\\+|/|x)\\s*", ", ");
        return n.trim();
    }

    private String firstArtistOnly(String simplified) {
        int c = simplified.indexOf(',');
        return c > 0 ? simplified.substring(0, c).trim() : simplified;
    }

    private boolean usable(LrcLibEntry r) {
        return (r.syncedLyrics() != null && !r.syncedLyrics().isBlank())
                || (r.plainLyrics() != null && !r.plainLyrics().isBlank());
    }

    private Optional<String> pickLyrics(LrcLibEntry entry) {
        if (entry == null) return Optional.empty();
        if (entry.syncedLyrics() != null && !entry.syncedLyrics().isBlank()) {
            return Optional.of(entry.syncedLyrics());
        }
        if (entry.plainLyrics() != null && !entry.plainLyrics().isBlank()) {
            return Optional.of(entry.plainLyrics());
        }
        return Optional.empty();
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Convierte comillas y guiones tipográficos a ASCII — causa #1 de fallos. */
    private String normalizeTypography(String s) {
        if (s == null) return null;
        return s
                .replace('‘', '\'')
                .replace('’', '\'')
                .replace('“', '"')
                .replace('”', '"')
                .replace('–', '-')
                .replace('—', '-')
                .replace('´', '\'')
                .replace('`', '\'');
    }

    private String cleanTitle(String s) {
        if (s == null) return null;
        String r = s;
        r = r.replaceAll("(?i)\\s*[\\(\\[]\\s*(feat\\.?|ft\\.?|featuring|with)\\s+[^\\)\\]]*[\\)\\]]", "");
        r = r.replaceAll("(?i)\\s+(feat\\.?|ft\\.?|featuring|with)\\s+.+$", "");
        r = r.replaceAll("(?i)\\s*[\\(\\[][^\\)\\]]*(remaster|remix|radio edit|album version|original mix|live|acoustic|explicit|clean|main version|main radio edit|extended|instrumental|deluxe|bonus)[^\\)\\]]*[\\)\\]]", "");
        r = r.replaceAll("(?i)\\s*-\\s*\\d{4}\\s+(remaster|remastered|version).*$", "");
        r = r.replaceAll("(?i)\\s*-\\s*(radio edit|album version|original mix|live|acoustic|explicit|clean|extended|instrumental).*$", "");
        return r.trim();
    }

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

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record LrcLibEntry(
            Long id,
            String artistName,
            String trackName,
            Integer duration,
            String syncedLyrics,
            String plainLyrics
    ) {}
}
