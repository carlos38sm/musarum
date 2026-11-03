package musarum.backend.enrichment;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
        String lyricsPath = null;
        if (meta.syncedLyrics() != null && !meta.syncedLyrics().isBlank()) {
            lyricsPath = lyricsWriter.write(song.getId(), meta.syncedLyrics());
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
                null
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
     * Aplica metadatos de una canción. Recarga la entidad DENTRO de la
     * transacción para evitar operar sobre un {@code Song} detached (que no
     * persistiría los cambios en BD, p. ej. al enriquecer en async desde
     * {@link EmendatiumAutoEnricher}).
     */
    @Transactional
    public void applySingle(Long songId, EnrichedMetadata meta) {
        Song song = songService.findEntityById(songId);
        applyMetadata(song, meta);
    }
}