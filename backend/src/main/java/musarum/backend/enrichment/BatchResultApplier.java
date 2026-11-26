package musarum.backend.enrichment;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import musarum.backend.album.Album;
import musarum.backend.album.AlbumService;
import musarum.backend.artist.Artist;
import musarum.backend.artist.ArtistService;
import musarum.backend.enrichment.dto.BatchResultLine;
import musarum.backend.enrichment.dto.EnrichedMetadata;
import musarum.backend.song.Song;
import musarum.backend.song.SongService;

@Component
class BatchResultApplier {

    private static final Logger log = LoggerFactory.getLogger(BatchResultApplier.class);
    private final SongService songService;
    private final ArtistService artistService;
    private final AlbumService albumService;
    private final LyricsWriter lyricsWriter;
    private final JsonlReader jsonlReader;
    private final String musicFolder;

    // Self-reference inyectada por Spring. Necesaria porque los métodos
    // applyArtistIsolated / applyAlbumIsolated están marcados @Transactional
    // y Spring solo aplica el proxy cuando la llamada ENTRA al bean desde
    // fuera. Si applySingle los llama directamente (this.x), Spring se
    // salta el proxy y no abre nueva tx. Con self.x() el proxy sí entra en
    // juego. @Lazy evita el ciclo de instanciación en el constructor.
    @Autowired
    @Lazy
    private BatchResultApplier self;

    BatchResultApplier(SongService songService,
                       ArtistService artistService,
                       AlbumService albumService,
                       LyricsWriter lyricsWriter,
                       JsonlReader jsonlReader,
                       @org.springframework.beans.factory.annotation.Value("${MUSARUM_MUSIC_PATH}") String musicFolder) {
        this.songService = songService;
        this.artistService = artistService;
        this.albumService = albumService;
        this.lyricsWriter = lyricsWriter;
        this.jsonlReader = jsonlReader;
        this.musicFolder = musicFolder;
    }

    @Transactional
    public void apply(String outputFile) {
        List<BatchResultLine> lines = jsonlReader.read(outputFile);

        int applied = 0;
        int skipped = 0;

        for (BatchResultLine line : lines) {
            if (!"OK".equals(line.status()) || line.metadata() == null) {
                skipped++;
                continue;
            }

            String fullPath = normalize(musicFolder + "/" + line.fileName());
            Optional<Song> songOpt = songService.findEntityByAudioPath(fullPath);

            if (songOpt.isEmpty()) {
                log.warn("No se encontró canción en base para el archivo: {}", fullPath);
                skipped++;
                continue;
            }

            applyMetadata(songOpt.get(), line.metadata());
            applied++;
        }

        log.info("Resultados del batch aplicados: {} canciones enriquecidas, {} omitidas", applied, skipped);
    }

    private void applyMetadata(Song song, EnrichedMetadata meta) {
        // Diagnóstico: Carlos reportaba canciones con tonalidad pero sin BPM.
        // Loggeamos lo que llega del wire para confirmar si el problema está
        // en el serializador (BPM se pierde entre Emendatium y backend) o en
        // el applier. Aparece en /admin/server/logs si el nivel es INFO.
        log.info("applyMetadata song={} bpm={} key={} rg={} lyrics={} coverUrl={}",
                song.getId(), meta.bpm(), meta.musicalKey(),
                meta.replayGainTrackGain(),
                meta.syncedLyrics() != null,
                meta.coverArt() != null ? meta.coverArt().sourceUrl() : "no");
        String lyricsPath = null;
        if (meta.syncedLyrics() != null && !meta.syncedLyrics().isBlank()) {
            // CLAVE: no sobreescribir letras ya guardadas. Si el usuario
            // (o una pasada anterior de Emendatium) ya puso una letra, la
            // respetamos aunque Emendatium traiga otra ahora. Para cambiar
            // una letra concreta existe POST /songs/{id}/lyrics/refresh.
            boolean alreadyHasLyrics =
                    song.getLyricsPath() != null
                    && !song.getLyricsPath().isBlank()
                    && java.nio.file.Files.exists(lyricsWriter.pathFor(song.getId()));
            if (!alreadyHasLyrics) {
                lyricsPath = lyricsWriter.write(song.getId(), meta.syncedLyrics());
            } else {
                log.info("Letra ya existente para song {} — no sobrescribimos", song.getId());
            }
        }

        song.applyEnrichment(
                meta.bpm(),
                meta.musicalKey(),
                meta.replayGainTrackGain(),
                lyricsPath,
                meta.mbReleaseId(),
                meta.isrc()
        );

        applyArtistEnrichment(song, meta);
        applyAlbumEnrichment(song, meta);
    }

    private void applyArtistEnrichment(Song song, EnrichedMetadata meta) {
        if (song.getArtists().isEmpty() || meta.artistInfo() == null) return;
        Artist primaryArtist = song.getArtists().iterator().next();
        artistService.applyEnrichment(
                primaryArtist.getId(),
                meta.mbArtistId(),
                meta.artistInfo().biography(),
                meta.artistInfo().country(),
                meta.artistInfo().imageUrl()
        );
    }

    private void applyAlbumEnrichment(Song song, EnrichedMetadata meta) {
        Album album = song.getAlbum();
        if (album == null) return;
        String coverUrl = meta.coverArt() != null ? meta.coverArt().sourceUrl() : null;
        albumService.applyEnrichment(album.getId(), meta.mbReleaseId(), meta.year(), coverUrl);
    }

    private String normalize(String path) {
        return path.replace("\\", "/");
    }

    /**
     * Aplica metadatos de una canción en TRES transacciones separadas:
     *   1) Escalares (bpm, key, rg, lyricsPath, mbid, isrc) — songService.
     *   2) Artist enrichment (bio, country, mbArtistId) — artistService.
     *   3) Album enrichment (mbRelease, year, coverUrl) — albumService.
     *
     * Antes era una sola tx grande. Cuando el commit disparaba
     * {@code TransientPropertyValueException} por una fila huérfana en
     * {@code song_artists}, se hacía rollback de TODO — incluido el bpm/key
     * ya calculado. El móvil no veía nada aunque el cálculo había costado
     * 20 s de CPU. Ahora si (2) o (3) fallan, los escalares de (1) ya
     * están commiteados y el usuario ve la Musicalidad.
     */
    public void applySingle(Long songId, EnrichedMetadata meta) {
        // Paso 1: escalares (nunca toca song.artists → no puede fallar por FK stale)
        String lyricsPath = null;
        if (meta.syncedLyrics() != null && !meta.syncedLyrics().isBlank()) {
            try {
                Song current = songService.findEntityById(songId);
                boolean alreadyHasLyrics = current.getLyricsPath() != null
                        && !current.getLyricsPath().isBlank()
                        && java.nio.file.Files.exists(lyricsWriter.pathFor(songId));
                if (!alreadyHasLyrics) {
                    lyricsPath = lyricsWriter.write(songId, meta.syncedLyrics());
                }
            } catch (Exception e) {
                log.warn("No se pudo guardar letra embebida id={}: {}", songId, e.getMessage());
            }
        }

        log.info("applyMetadata song={} bpm={} key={} rg={} lyrics={} coverUrl={}",
                songId, meta.bpm(), meta.musicalKey(), meta.replayGainTrackGain(),
                lyricsPath != null,
                meta.coverArt() != null ? meta.coverArt().sourceUrl() : "no");

        songService.applyScalarEnrichment(songId,
                meta.bpm(), meta.musicalKey(), meta.replayGainTrackGain(),
                lyricsPath, meta.mbReleaseId(), meta.isrc());

        // Paso 2: artist — aislado. Si tira TransientPropertyValueException
        // porque song_artists apunta a un Artist fantasma, se registra y
        // seguimos: los escalares ya están guardados.
        try {
            self.applyArtistIsolated(songId, meta);
        } catch (Exception e) {
            log.warn("Enrichment de artist falló id={} (escalares guardados OK): {}",
                    songId, e.getMessage());
        }

        // Paso 3: album — mismo criterio.
        try {
            self.applyAlbumIsolated(songId, meta);
        } catch (Exception e) {
            log.warn("Enrichment de album falló id={} (escalares guardados OK): {}",
                    songId, e.getMessage());
        }
    }

    @Transactional
    public void applyArtistIsolated(Long songId, EnrichedMetadata meta) {
        if (meta.artistInfo() == null) return;
        Song song = songService.findEntityById(songId);
        if (song.getArtists().isEmpty()) return;
        Artist primaryArtist = song.getArtists().iterator().next();
        artistService.applyEnrichment(
                primaryArtist.getId(),
                meta.mbArtistId(),
                meta.artistInfo().biography(),
                meta.artistInfo().country(),
                meta.artistInfo().imageUrl()
        );
    }

    @Transactional
    public void applyAlbumIsolated(Long songId, EnrichedMetadata meta) {
        Song song = songService.findEntityById(songId);
        Album album = song.getAlbum();
        if (album == null) return;
        String coverUrl = meta.coverArt() != null ? meta.coverArt().sourceUrl() : null;
        albumService.applyEnrichment(album.getId(), meta.mbReleaseId(), meta.year(), coverUrl);
    }

    /**
     * Variante de {@link #applySingle} que solo toca los escalares de la
     * canción (bpm, key, replaygain, letras, isrc, mbRelease) y NO los
     * campos de artista ni álbum. Para "Re-enriquecer solo canciones":
     * el OWNER ya curó las fotos/bios de artistas y las covers de álbum
     * a mano y no quiere que esta pasada se las pise.
     */
    public void applyScalarsOnly(Long songId, EnrichedMetadata meta) {
        String lyricsPath = null;
        if (meta.syncedLyrics() != null && !meta.syncedLyrics().isBlank()) {
            try {
                Song current = songService.findEntityById(songId);
                boolean alreadyHasLyrics = current.getLyricsPath() != null
                        && !current.getLyricsPath().isBlank()
                        && java.nio.file.Files.exists(lyricsWriter.pathFor(songId));
                if (!alreadyHasLyrics) {
                    lyricsPath = lyricsWriter.write(songId, meta.syncedLyrics());
                }
            } catch (Exception e) {
                log.warn("No se pudo guardar letra embebida id={}: {}", songId, e.getMessage());
            }
        }

        songService.applyScalarEnrichment(songId,
                meta.bpm(), meta.musicalKey(), meta.replayGainTrackGain(),
                lyricsPath, meta.mbReleaseId(), meta.isrc());
    }
}