package musarum.backend.enrichment.lyrics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.Normalizer;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/**
 * Proveedor de letras basado en la API pública de NetEase Cloud Music
 * (music.163.com). Última red de seguridad cuando LrcLib no tiene la
 * canción: NetEase tiene un catálogo enorme con letras sincronizadas,
 * incluso de artistas occidentales. No requiere API key.
 *
 * Protocolo:
 *   1. GET /api/search/get?s=<query>&type=1&limit=5
 *      → result.songs[].id → cogemos el primero cuyo artista encaje.
 *   2. GET /api/song/lyric?id=<id>&lv=1&kv=1&tv=-1
 *      → lrc.lyric contiene el LRC sincronizado.
 *
 * Requiere Referer o devuelve 403. Rechaza respuestas sin timestamps
 * (filtra instrumentales marcados por NetEase).
 */
@Component
@Order(3)
class NeteaseLyricsProvider implements LyricsProvider {

    private static final Logger log = LoggerFactory.getLogger(NeteaseLyricsProvider.class);
    private static final String SEARCH_URL = "https://music.163.com/api/search/get";
    private static final String LYRIC_URL  = "https://music.163.com/api/song/lyric";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(3);
    private static final Duration READ_TIMEOUT    = Duration.ofSeconds(5);

    private final RestClient http = RestClient.builder()
            .requestFactory(buildRequestFactory())
            .defaultHeader("Referer", "https://music.163.com/")
            .defaultHeader("User-Agent",
                    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36")
            .defaultHeader("Accept", "application/json")
            .build();

    private static JdkClientHttpRequestFactory buildRequestFactory() {
        HttpClient jdk = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();
        JdkClientHttpRequestFactory f = new JdkClientHttpRequestFactory(jdk);
        f.setReadTimeout(READ_TIMEOUT);
        return f;
    }

    @Override
    public String name() {
        return "NetEase";
    }

    @Override
    public Optional<String> fetch(String artist, String title, String album, Integer durationSec, Path audioPath) {
        if (artist == null || title == null || artist.isBlank() || title.isBlank()) {
            return Optional.empty();
        }
        log.info("  🔹 NetEase buscando '{} - {}'", artist, title);
        Optional<Long> songId = findSongId(artist, title);
        if (songId.isEmpty()) {
            log.info("  ✗ NetEase: sin canción tras verificar artista");
            return Optional.empty();
        }
        return fetchLyric(songId.get());
    }

    private Optional<Long> findSongId(String artist, String title) {
        try {
            String query = artist + " " + title;
            String url = SEARCH_URL + "?s=" + enc(query) + "&type=1&limit=5";
            NeteaseSearchResponse resp = http.get().uri(URI.create(url))
                    .retrieve().body(NeteaseSearchResponse.class);
            if (resp == null || resp.result == null || resp.result.songs == null) {
                return Optional.empty();
            }
            String expected = simplifyArtist(artist);
            for (NeteaseSong s : resp.result.songs) {
                if (s.artists == null || s.artists.isEmpty()) continue;
                String primary = simplifyArtist(s.artists.get(0).name);
                if (primary.equals(expected)
                        || primary.contains(expected)
                        || expected.contains(primary)) {
                    log.info("    → match id={} artista='{}'", s.id, s.artists.get(0).name);
                    return Optional.of(s.id);
                }
            }
            return Optional.empty();
        } catch (Exception e) {
            log.info("    → fallo buscando: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> fetchLyric(long songId) {
        try {
            String url = LYRIC_URL + "?id=" + songId + "&lv=1&kv=1&tv=-1";
            NeteaseLyricResponse resp = http.get().uri(URI.create(url))
                    .retrieve().body(NeteaseLyricResponse.class);
            if (resp == null) return Optional.empty();
            String synced = resp.lrc != null ? resp.lrc.lyric : null;
            if (synced != null && !synced.isBlank() && synced.contains("[")) {
                log.info("  ✓ NetEase: letra sincronizada ({} chars)", synced.length());
                return Optional.of(synced);
            }
            return Optional.empty();
        } catch (Exception e) {
            log.info("    → fallo descargando letra: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private String simplifyArtist(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.trim().toLowerCase(), Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        if (n.startsWith("the ")) n = n.substring(4);
        n = n.replaceAll("\\s*(feat\\.?|ft\\.?|featuring|with)\\s+", ", ");
        n = n.replaceAll("\\s*(&|\\+|/|x)\\s*", ", ");
        int comma = n.indexOf(',');
        if (comma > 0) n = n.substring(0, comma);
        return n.trim();
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NeteaseSearchResponse { public NeteaseSearchResult result; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NeteaseSearchResult { public List<NeteaseSong> songs; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NeteaseSong { public long id; public String name; public List<NeteaseArtist> artists; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NeteaseArtist { public String name; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NeteaseLyricResponse { public NeteaseLyric lrc; }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NeteaseLyric { public String lyric; }
}
