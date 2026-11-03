package musarum.backend.song;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import musarum.backend.favorite.FavoriteRepository;
import musarum.backend.playlist.PlaylistEntryRepository;
import musarum.backend.reproduction.ReproductionRepository;
import musarum.backend.song.exception.SongNotFoundException;

/**
 * Borra una canción del servidor por completo: fila de la DB, referencias
 * (favoritos, entradas de playlist, reproducciones), el archivo de audio
 * del disco y el .lrc local si existe. Pensado para la opción "Borrar
 * canción" del long-press del OWNER en el móvil.
 *
 * El orden importa:
 *  1. Limpiar referencias (favorites, playlist_entries, reproductions) —
 *     las FK no tienen ON DELETE CASCADE en la DB, así que si no las
 *     borramos primero el DELETE de songs fallaría.
 *  2. Borrar la canción vía repository.delete(song) — Hibernate emite el
 *     DELETE sobre song_artists (ManyToMany) automáticamente.
 *  3. Borrar el fichero físico del disco y su .lrc hermano (si los hay).
 *
 * Si el borrado de la DB falla, el archivo del disco NO se toca. Si el
 * archivo del disco falla al borrar (p. ej. permisos), la DB ya está
 * limpia pero el fichero queda huérfano: lo logueamos como warning y
 * seguimos. El scanner no lo re-importará porque su audio_path ya no
 * existe en DB; en el siguiente scan sí lo trataría como nuevo. El OWNER
 * puede borrarlo a mano desde el PC si Musarum no pudo.
 */
@Service
public class SongDeletionService {

    private static final Logger log = LoggerFactory.getLogger(SongDeletionService.class);

    private final SongRepository songRepository;
    private final FavoriteRepository favoriteRepository;
    private final PlaylistEntryRepository playlistEntryRepository;
    private final ReproductionRepository reproductionRepository;

    SongDeletionService(SongRepository songRepository,
                        FavoriteRepository favoriteRepository,
                        PlaylistEntryRepository playlistEntryRepository,
                        ReproductionRepository reproductionRepository) {
        this.songRepository = songRepository;
        this.favoriteRepository = favoriteRepository;
        this.playlistEntryRepository = playlistEntryRepository;
        this.reproductionRepository = reproductionRepository;
    }

    @Transactional
    public DeletionResult deleteCompletely(Long songId) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new SongNotFoundException(songId));
        String audioPath = song.getAudioPath();
        String lyricsPath = song.getLyricsPath();

        long favorites = favoriteRepository.deleteBySongId(songId);
        long entries = playlistEntryRepository.deleteBySongId(songId);
        long plays = reproductionRepository.deleteBySongId(songId);

        songRepository.delete(song);
        songRepository.flush();  // asegura que el DELETE llega antes de tocar el disco

        boolean fileDeleted = deleteFile(audioPath);
        boolean lyricsDeleted = (lyricsPath != null) && deleteFile(lyricsPath);

        log.info("Canción {} eliminada: favoritos={}, entradas={}, reproducciones={}, "
                        + "archivo={}, letras={}",
                songId, favorites, entries, plays, fileDeleted, lyricsDeleted);

        return new DeletionResult(audioPath, favorites, entries, plays, fileDeleted);
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
