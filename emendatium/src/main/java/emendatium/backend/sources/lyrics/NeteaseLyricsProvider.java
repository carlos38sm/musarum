package emendatium.backend.sources.lyrics;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.List;
import java.util.Optional;

/**
 * Proveedor de letras basado en la API pública de NetEase Cloud Music
 * (api.music.163.com). Última red de seguridad cuando LrcLib no tiene la
 * canción: NetEase tiene un catálogo enorme con letras sincronizadas,
 * incluso de artistas occidentales. No requiere API key.
 *
 * Protocolo (dos pasos):
 *   1. GET /api/search/get?s=<query>&type=1&limit=5
 *      → result.songs[].id → cogemos el primero cuyo artista encaje.
 *   2. GET /api/song/lyric?id=<id>&lv=1&kv=1&tv=-1
 *      → lrc.lyric contiene el LRC sincronizado.
 *
 * Verificación de artista (anti-suplantación): rechazamos resultados cuyo
 * primer artista no coincida de forma tolerante con el buscado.
 */
@Slf4j
@Component
@Order(3)
public class NeteaseLyricsProvider implements LyricsProvider {

    private static final String SEARCH_URL = "https://music.163.com/api/search/get";
    private static final String LYRIC_URL  = "https://music.163.com/api/song/lyric";

    private final RestClient http;

    public NeteaseLyricsProvider(RestClient enricherRestClient) {
        this.http = enricherRestClient;
    }

    @Override
    public String name() {
        return "NetEase";
    }

    @Override
    public Optional<String> fetch(String artist, String title, String album, Integer durationSec, Path mp3Path) {
        if (artist == null || title == null || artist.isBlank() || title.isBlank()) {
            return Optional.empty();
        }
        log.info("    🔹 NetEase: buscando '{} - {}'", artist, title);

        Optional<Long> songId = findSongId(artist, title);
        if (songId.isEmpty()) {
            log.info("    ✗ NetEase: sin canción tras verificar artista");
            return Optional.empty();
        }
        return fetchLyric(songId.get());
    }

    private Optional<Long> findSongId(String artist, String title) {
        try {
            String query = artist + " " + title;
            String url = SEARCH_URL + "?s=" + encode(query) + "&type=1&limit=5";
            // NetEase mira el Referer; sin él responde 403.
            NeteaseSearchResponse resp = http.get()
                    .uri(url)
                    .header("Referer", "https://music.163.com/")
                    .retrieve()
                    .body(NeteaseSearchResponse.class);
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
                    log.info("        → match: id={} artista='{}'", s.id, s.artists.get(0).name);
                    return Optional.of(s.id);
                }
            }
            log.info("        → {} candidatos, ninguno con artista coincidente", resp.result.songs.size());
            return Optional.empty();
        } catch (Exception e) {
            log.info("        → fallo buscando: {}", e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<String> fetchLyric(long songId) {
        try {
            String url = LYRIC_URL + "?id=" + songId + "&lv=1&kv=1&tv=-1";
            NeteaseLyricResponse resp = http.get()
                    .uri(url)
                    .header("Referer", "https://music.163.com/")
                    .retrieve()
                    .body(NeteaseLyricResponse.class);
            if (resp == null) return Optional.empty();

            String synced = resp.lrc != null ? resp.lrc.lyric : null;
            if (synced != null && !synced.isBlank() && synced.contains("[")) {
                log.info("    ✓ NetEase: letra sincronizada ({} chars)", synced.length());
                return Optional.of(synced);
            }
            // NetEase marca canciones instrumentales con lrc vacío + nolyric=true.
            // No las propagamos — queremos que la cascada acabe en "no hay letra".
            return Optional.empty();
        } catch (Exception e) {
            log.info("        → fallo descargando letra: {}", e.getMessage());
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

    private String encode(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    // ── DTOs mínimos de la respuesta ─────────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NeteaseSearchResponse {
        public NeteaseSearchResult result;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NeteaseSearchResult {
        public List<NeteaseSong> songs;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NeteaseSong {
        public long id;
        public String name;
        public List<NeteaseArtist> artists;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NeteaseArtist {
        public String name;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NeteaseLyricResponse {
        public NeteaseLyric lrc;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private static class NeteaseLyric {
        public String lyric;
    }
}
