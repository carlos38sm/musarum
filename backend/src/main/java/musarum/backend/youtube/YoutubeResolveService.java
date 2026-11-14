package musarum.backend.youtube;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import musarum.backend.song.Song;
import musarum.backend.song.SongRepository;
import musarum.backend.song.exception.SongNotFoundException;

/**
 * Resuelve el videoId de YouTube para una canción haciendo scraping ligero
 * de {@code youtube.com/results?search_query=...}. No requiere API key.
 *
 * El HTML de YouTube inyecta una variable {@code ytInitialData} con todos
 * los resultados; buscamos el PRIMER {@code "videoId":"XXXXXXXXXXX"} del
 * JSON embebido (11 chars alfanuméricos + "_-"). Está documentado como
 * patrón estable por scrapers desde hace años; si YouTube lo cambia,
 * basta ajustar el regex.
 *
 * El resultado se cachea en la fila de la canción (columna
 * {@code youtube_video_id} + timestamp). Peticiones sucesivas son
 * instantáneas y no tocan YouTube.
 */
@Service
public class YoutubeResolveService {

    private static final Logger log = LoggerFactory.getLogger(YoutubeResolveService.class);
    private static final Pattern VIDEO_ID_RE = Pattern.compile("\"videoId\":\"([A-Za-z0-9_-]{11})\"");
    private static final String USER_AGENT =
            "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";
    private static final String SENTINEL_NO_MATCH = "__none__";

    private final SongRepository songRepository;
    private final HttpClient httpClient;

    YoutubeResolveService(SongRepository songRepository) {
        this.songRepository = songRepository;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(4))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * Devuelve el videoId cacheado o lo resuelve en caliente. Si refresh=true,
     * ignora la caché. Si no se encuentra, devuelve null y marca la canción
     * con un sentinel interno para no spamear YouTube con la misma búsqueda
     * hasta que pasen unos días (política simple: el móvil puede forzar
     * refresh si al usuario le interesa).
     */
    @Transactional
    public String resolveFor(Long songId, boolean refresh) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new SongNotFoundException(songId));
        String cached = song.getYoutubeVideoId();
        if (!refresh && cached != null) {
            return cached.equals(SENTINEL_NO_MATCH) ? null : cached;
        }

        String artist = (song.getArtists().isEmpty())
                ? ""
                : song.getArtists().iterator().next().getName();
        String query = (artist + " " + song.getTitle()).trim();
        if (query.isEmpty()) return null;

        String videoId = scrapeFirstVideoId(query);
        song.setYoutubeResolution(videoId != null ? videoId : SENTINEL_NO_MATCH);
        songRepository.save(song);
        return videoId;
    }

    /**
     * Hace la petición a YouTube y extrae el primer videoId del HTML. Si YouTube
     * devuelve algo no parseable (403, captcha, HTML cambiado), devuelve null.
     * El timeout es corto (6 s) porque el móvil está esperando con un spinner.
     */
    private String scrapeFirstVideoId(String query) {
        String encoded = URLEncoder.encode(query, StandardCharsets.UTF_8);
        String url = "https://www.youtube.com/results?search_query=" + encoded
                + "&sp=EgIQAQ%253D%253D";  // filtro "Videos" (no canales, no playlists)
        HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept-Language", "es-ES,es;q=0.9,en;q=0.8")
                .timeout(Duration.ofSeconds(6))
                .GET()
                .build();
        try {
            HttpResponse<String> res = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() != 200) {
                log.warn("YouTube respondió {} a '{}'", res.statusCode(), query);
                return null;
            }
            Matcher m = VIDEO_ID_RE.matcher(res.body());
            if (m.find()) return m.group(1);
            log.warn("YouTube devolvió HTML sin videoId para '{}'", query);
            return null;
        } catch (Exception e) {
            log.warn("Fallo scrapeando YouTube para '{}': {}", query, e.getMessage());
            return null;
        }
    }
}
