package musarum.backend.song;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import musarum.backend.enrichment.LyricsWriter;
import musarum.backend.enrichment.lyrics.LyricsProvider;
import musarum.backend.song.exception.LyricsNotFoundException;
import musarum.backend.song.exception.SongNotFoundException;

/**
 * Orquesta la obtención de letras separando lo transaccional (lectura de BD,
 * cache) de las llamadas de red (que tardan segundos y NO deben mantener una
 * conexión del pool abierta).
 *
 * Flujo:
 *   1. {@link #readLocalOrSnapshot}: tx corta readOnly. Si hay letra local
 *      en disco (apuntada por {@code lyrics_path} o en la ruta estándar),
 *      la devuelve. Si no, devuelve un snapshot con artista/título/duración/
 *      audioPath para la búsqueda online.
 *   2. Si no había local, recorre {@link LyricsProvider}s en orden (LocalTag
 *      → LRCLib → NetEase) FUERA de la tx.
 *   3. Si alguno devuelve, {@link #cacheLyrics} abre una tx corta para
 *      persistir el path.
 *
 * Variante {@link #refresh}: ignora la letra cacheada y busca de nuevo —
 * útil cuando la cacheada es la de otro tema o quedó incorrecta.
 */
@Service
public class LyricsLookupService {

    private static final Logger log = LoggerFactory.getLogger(LyricsLookupService.class);

    private final SongRepository songRepository;
    private final List<LyricsProvider> providers;
    private final LyricsWriter lyricsWriter;
    private final LyricsLookupService self;

    LyricsLookupService(SongRepository songRepository,
                        List<LyricsProvider> providers,
                        LyricsWriter lyricsWriter,
                        @Lazy @Autowired LyricsLookupService self) {
        this.songRepository = songRepository;
        this.providers = providers;
        this.lyricsWriter = lyricsWriter;
        this.self = self;
        log.info("LyricsLookupService activo con providers: {}",
                providers.stream().map(LyricsProvider::name).toList());
    }

    public String getLyrics(Long id) {
        LyricsLookup lookup = self.readLocalOrSnapshot(id);
        if (lookup.content() != null) return lookup.content();
        return fetchAndCache(id, lookup);
    }

    /**
     * Ignora la letra cacheada y lanza la búsqueda online de cero. Útil
     * cuando la cacheada es incorrecta o incompleta. Mantiene el fichero
     * local si lo había (y lo sobrescribe al encontrar nueva).
     */
    public String refreshLyrics(Long id) {
        LyricsLookup snapshot = self.loadSnapshot(id);
        return fetchAndCache(id, snapshot);
    }

    private String fetchAndCache(Long id, LyricsLookup lookup) {
        for (LyricsProvider provider : providers) {
            Optional<String> result = provider.fetch(
                    lookup.artistName(),
                    lookup.title(),
                    lookup.album(),
                    lookup.durationSec(),
                    lookup.audioPath()
            );
            if (result.isPresent() && !result.get().isBlank()) {
                log.info("🎵 Letra aportada por {} para id={}", provider.name(), id);
                self.cacheLyrics(id, result.get());
                return result.get();
            }
        }
        log.info("✗ Ningún proveedor devolvió letra para id={}", id);
        throw new LyricsNotFoundException(id);
    }

    @Transactional(readOnly = true)
    public LyricsLookup readLocalOrSnapshot(Long id) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));

        String lyricsPath = song.getLyricsPath();
        if (lyricsPath != null && !lyricsPath.isBlank()) {
            try {
                Path path = Path.of(lyricsPath);
                if (Files.exists(path)) {
                    return new LyricsLookup(
                            Files.readString(path, StandardCharsets.UTF_8),
                            null, null, null, null, null
                    );
                }
            } catch (IOException ignored) { /* fallback */ }
        }

        Path standardPath = lyricsWriter.pathFor(song.getId());
        if (Files.exists(standardPath)) {
            try {
                String content = Files.readString(standardPath, StandardCharsets.UTF_8);
                song.setLyricsPath(standardPath.toString().replace("\\", "/"));
                return new LyricsLookup(content, null, null, null, null, null);
            } catch (IOException ignored) { /* fallback */ }
        }

        return snapshotFrom(song);
    }

    /**
     * Igual que readLocalOrSnapshot pero SIN leer de disco: devuelve siempre
     * el snapshot — lo usa {@link #refreshLyrics} para forzar búsqueda online.
     */
    @Transactional(readOnly = true)
    public LyricsLookup loadSnapshot(Long id) {
        Song song = songRepository.findById(id)
                .orElseThrow(() -> new SongNotFoundException(id));
        return snapshotFrom(song);
    }

    private LyricsLookup snapshotFrom(Song song) {
        String artistName = song.getArtists().stream()
                .findFirst().map(a -> a.getName()).orElse(null);
        String albumTitle = song.getAlbum() != null ? song.getAlbum().getTitle() : null;
        Integer durationSec = song.getDurationMs() != null ? song.getDurationMs() / 1000 : null;
        Path audioPath = song.getAudioPath() != null ? Path.of(song.getAudioPath()) : null;
        return new LyricsLookup(null, artistName, song.getTitle(), albumTitle, durationSec, audioPath);
    }

    @Transactional
    public void cacheLyrics(Long id, String lyrics) {
        try {
            String saved = lyricsWriter.write(id, lyrics);
            songRepository.findById(id).ifPresent(s -> s.setLyricsPath(saved));
            log.info("Letra cacheada en disco para id={}", id);
        } catch (Exception e) {
            log.warn("No se pudo persistir cache de letra id={}: {}", id, e.getMessage());
        }
    }

    public record LyricsLookup(
            String content,
            String artistName,
            String title,
            String album,
            Integer durationSec,
            Path audioPath
    ) {}
}
