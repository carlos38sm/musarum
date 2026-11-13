package musarum.backend.song;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import musarum.backend.album.Album;
import musarum.backend.album.AlbumRepository;
import musarum.backend.artist.Artist;
import musarum.backend.artist.ArtistRepository;
import musarum.backend.favorite.FavoriteRepository;
import musarum.backend.genre.Genre;
import musarum.backend.genre.GenreRepository;
import musarum.backend.playlist.PlaylistEntryRepository;
import musarum.backend.reproduction.ReproductionRepository;
import musarum.backend.shared.CoverStorage;
import musarum.backend.song.exception.SongNotFoundException;

/**
 * Borra una canción del servidor por completo:
 *   1. Referencias (favoritos, playlist_entries, reproducciones).
 *   2. La fila de songs (Hibernate limpia song_artists ManyToMany).
 *   3. El fichero de audio y su .lrc hermano del disco.
 *   4. Artistas / álbum / género HUÉRFANOS que dejó la canción — si eran su
 *      único referenciador, no tiene sentido que sigan en DB (ArtistsScreen
 *      mostraba fantasmas sin canciones). También barre su imagen local.
 *
 * Si la DB falla, no se toca el disco. Si el disco falla, la DB ya está
 * limpia; lo logueamos como warning.
 */
@Service
public class SongDeletionService {

    private static final Logger log = LoggerFactory.getLogger(SongDeletionService.class);

    private final SongRepository songRepository;
    private final FavoriteRepository favoriteRepository;
    private final PlaylistEntryRepository playlistEntryRepository;
    private final ReproductionRepository reproductionRepository;
    private final ArtistRepository artistRepository;
    private final AlbumRepository albumRepository;
    private final GenreRepository genreRepository;
    private final CoverStorage coverStorage;

    SongDeletionService(SongRepository songRepository,
                        FavoriteRepository favoriteRepository,
                        PlaylistEntryRepository playlistEntryRepository,
                        ReproductionRepository reproductionRepository,
                        ArtistRepository artistRepository,
                        AlbumRepository albumRepository,
                        GenreRepository genreRepository,
                        CoverStorage coverStorage) {
        this.songRepository = songRepository;
        this.favoriteRepository = favoriteRepository;
        this.playlistEntryRepository = playlistEntryRepository;
        this.reproductionRepository = reproductionRepository;
        this.artistRepository = artistRepository;
        this.albumRepository = albumRepository;
        this.genreRepository = genreRepository;
        this.coverStorage = coverStorage;
    }

    @Transactional
    public DeletionResult deleteCompletely(Long songId) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new SongNotFoundException(songId));
        String audioPath = song.getAudioPath();
        String lyricsPath = song.getLyricsPath();

        // Snapshot de las entidades relacionadas ANTES de borrar: los artists
        // se cargan aquí (lazy ManyToMany) dentro de la tx; después del
        // delete+flush ya no se podrían iterar.
        Set<Long> artistIds = new HashSet<>();
        for (Artist a : song.getArtists()) artistIds.add(a.getId());
        Long albumId = song.getAlbum() != null ? song.getAlbum().getId() : null;
        Long genreId = song.getGenre() != null ? song.getGenre().getId() : null;

        long favorites = favoriteRepository.deleteBySongId(songId);
        long entries = playlistEntryRepository.deleteBySongId(songId);
        long plays = reproductionRepository.deleteBySongId(songId);

        songRepository.delete(song);
        songRepository.flush();  // asegura que el DELETE llega antes de contar huérfanos

        boolean fileDeleted = deleteFile(audioPath);
        boolean lyricsDeleted = (lyricsPath != null) && deleteFile(lyricsPath);

        int artistsPurged = purgeOrphanArtists(artistIds);
        int albumsPurged = purgeOrphanAlbum(albumId);
        int genresPurged = purgeOrphanGenre(genreId);

        log.info("Canción {} eliminada: favoritos={} entradas={} plays={} "
                        + "archivo={} letras={} artistasHuérfanos={} álbumHuérfano={} géneroHuérfano={}",
                songId, favorites, entries, plays, fileDeleted, lyricsDeleted,
                artistsPurged, albumsPurged, genresPurged);

        return new DeletionResult(audioPath, favorites, entries, plays, fileDeleted);
    }

    /** Borra cada artista cuyo countByArtistId tras el delete sea 0, y su imagen. */
    private int purgeOrphanArtists(Set<Long> artistIds) {
        int purged = 0;
        for (Long id : artistIds) {
            if (songRepository.countByArtistId(id) == 0) {
                artistRepository.deleteById(id);
                coverStorage.deleteArtistImage(id);
                purged++;
            }
        }
        return purged;
    }

    private int purgeOrphanAlbum(Long albumId) {
        if (albumId == null) return 0;
        if (songRepository.countByAlbumId(albumId) > 0) return 0;
        albumRepository.deleteById(albumId);
        coverStorage.deleteAlbumCover(albumId);
        return 1;
    }

    private int purgeOrphanGenre(Long genreId) {
        if (genreId == null) return 0;
        if (songRepository.countByGenreId(genreId) > 0) return 0;
        genreRepository.deleteById(genreId);
        return 1;
    }

    private boolean deleteFile(String path) {
        if (path == null || path.isBlank()) return false;
        try {
            boolean deleted = Files.deleteIfExists(Path.of(path));
            if (!deleted) log.warn("Fichero no existía al borrar: {}", path);
            return deleted;
        } catch (IOException e) {
            log.warn("No se pudo borrar fichero {}: {}", path, e.getMessage());
            return false;
        }
    }

    public record DeletionResult(
            String audioPath,
            long favoritesRemoved,
            long playlistEntriesRemoved,
            long playsRemoved,
            boolean fileDeleted
    ) {}
}
