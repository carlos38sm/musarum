package musarum.backend.song;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import musarum.backend.album.Album;
import musarum.backend.album.AlbumService;
import musarum.backend.artist.Artist;
import musarum.backend.artist.ArtistService;
import musarum.backend.genre.Genre;
import musarum.backend.genre.GenreService;
import musarum.backend.song.dto.SongDetailsDto;
import musarum.backend.song.dto.TagEditRequest;
import musarum.backend.song.exception.SongNotFoundException;
import musarum.backend.favorite.FavoriteChecker;

/**
 * Edición manual de los tags ID3 + metadatos JPA de una canción. Lo usa
 * el botón "Editar tags" del móvil (InfoScreen). Operación OWNER-only por
 * su naturaleza destructiva: cambiar el artista a mano crea un artista
 * nuevo si no existe, y eso puede acabar con "la casa" llena de ghosts si
 * un MEMBER se equivoca.
 *
 * El contrato:
 *   - Solo los campos no-null del request se tocan (patch parcial).
 *   - Los artistas se separan por "," y "; " (varios).
 *   - Si {@code renameFile=true} el fichero físico se renombra a
 *     "{artist} - {title}.{ext}" en su directorio.
 *   - Si el rename falla, los tags y la DB SÍ quedan actualizados (no
 *     revertimos): el OWNER lo verá en los logs y puede reintentar.
 */
@Service
public class TagEditService {

    private static final Logger log = LoggerFactory.getLogger(TagEditService.class);

    private final SongRepository songRepository;
    private final SongMapper songMapper;
    private final ArtistService artistService;
    private final AlbumService albumService;
    private final GenreService genreService;
    private final FavoriteChecker favoriteChecker;

    TagEditService(SongRepository songRepository,
                   SongMapper songMapper,
                   ArtistService artistService,
                   AlbumService albumService,
                   GenreService genreService,
                   FavoriteChecker favoriteChecker) {
        this.songRepository = songRepository;
        this.songMapper = songMapper;
        this.artistService = artistService;
        this.albumService = albumService;
        this.genreService = genreService;
        this.favoriteChecker = favoriteChecker;
    }

    @Transactional
    public SongDetailsDto editTags(Long songId, Long requesterId, TagEditRequest req) {
        Song song = songRepository.findById(songId)
                .orElseThrow(() -> new SongNotFoundException(songId));

        // 1) Actualiza el fichero en disco con jaudiotagger (antes de tocar DB).
        //    Si el fichero no existe o no se puede abrir, rechazamos ahora
        //    porque sin eso los tags ID3 nunca cuadrarían con la DB.
        String audioPath = song.getAudioPath();
        File file = new File(audioPath);
        if (!file.exists()) {
            throw new IllegalStateException("El fichero no existe: " + audioPath);
        }

        try {
            AudioFile af = AudioFileIO.read(file);
            Tag tag = af.getTagOrCreateAndSetDefault();
            boolean dirty = false;
            if (req.title() != null)        { setField(tag, FieldKey.TITLE, req.title()); dirty = true; }
            if (req.artist() != null)       { setField(tag, FieldKey.ARTIST, req.artist()); dirty = true; }
            if (req.album() != null)        { setField(tag, FieldKey.ALBUM, req.album()); dirty = true; }
            if (req.genre() != null)        { setField(tag, FieldKey.GENRE, req.genre()); dirty = true; }
            if (req.year() != null)         { setField(tag, FieldKey.YEAR, req.year().toString()); dirty = true; }
            if (req.trackNumber() != null)  { setField(tag, FieldKey.TRACK, req.trackNumber().toString()); dirty = true; }
            if (dirty) AudioFileIO.write(af);
        } catch (Exception e) {
            // Si no podemos escribir el tag, no tiene sentido tocar la DB —
            // quedaría desalineada con el fichero del disco.
            throw new IllegalStateException("No se pudo reescribir el tag: " + e.getMessage(), e);
        }

        // 2) Aplica cambios a la fila JPA.
        if (req.title() != null && !req.title().isBlank()) song.setTitle(req.title().trim());
        if (req.year() != null) song.setYear(req.year());
        if (req.trackNumber() != null) song.setTrackNumber(req.trackNumber());

        if (req.artist() != null && !req.artist().isBlank()) {
            Set<Artist> newArtists = new HashSet<>();
            for (String name : splitArtists(req.artist())) {
                newArtists.add(artistService.findOrCreate(name));
            }
            if (!newArtists.isEmpty()) song.setArtists(newArtists);
        }

        if (req.album() != null && !req.album().isBlank()) {
            // Para resolver el álbum necesitamos un artista "representativo";
            // tomamos el primero de los artists actualizados o existentes.
            Artist representative = song.getArtists().stream().findFirst().orElse(null);
            if (representative != null) {
                Album album = albumService.findOrCreate(req.album().trim(), representative);
                song.setAlbum(album);
            }
        }

        if (req.genre() != null && !req.genre().isBlank()) {
            Genre g = genreService.findOrCreate(req.genre().trim());
            song.setGenre(g);
        }

        // 3) Rename opcional del fichero físico. Si falla, los tags + DB ya
        //    se han actualizado — no revertimos. El OWNER lo reintenta.
        if (Boolean.TRUE.equals(req.renameFile())) {
            tryRenameFile(song, file);
        }

        songRepository.save(song);
        boolean isFavorite = favoriteChecker.isFavorite(requesterId, songId);
        return songMapper.toDetails(song, isFavorite);
    }

    private void tryRenameFile(Song song, File current) {
        String ext = extensionOf(current.getName());
        String artist = song.getArtists().stream()
                .map(Artist::getName).findFirst().orElse("Desconocido");
        String title = song.getTitle();
        String baseName = sanitize(artist) + " - " + sanitize(title);
        File target = new File(current.getParentFile(), baseName + "." + ext);

        // Si ya se llama así, nada que hacer.
        if (Objects.equals(target.getAbsolutePath(), current.getAbsolutePath())) return;

        // Si hay choque de nombre, añadimos sufijo numérico.
        File dest = target;
        int n = 2;
        while (dest.exists()) {
            dest = new File(current.getParentFile(), baseName + " (" + n + ")." + ext);
            n++;
        }

        try {
            Path moved = Files.move(current.toPath(), dest.toPath(),
                    StandardCopyOption.ATOMIC_MOVE);
            String newPath = moved.toString().replace("\\", "/");
            song.updateAudioPath(newPath);
            log.info("Tag editor renombró fichero: {} → {}", current.getAbsolutePath(), newPath);
        } catch (Exception e) {
            log.warn("No se pudo renombrar el fichero id={}: {}", song.getId(), e.getMessage());
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static void setField(Tag tag, FieldKey key, String value) throws Exception {
        tag.deleteField(key);
        tag.setField(key, value);
    }

    /** Separa por "," y "; " manteniendo el orden de aparición. */
    private static java.util.List<String> splitArtists(String raw) {
        return java.util.Arrays.stream(raw.split("[;,]"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .distinct()
                .toList();
    }

    /** Quita caracteres prohibidos para nombres de fichero en Windows/Linux. */
    private static String sanitize(String s) {
        String cleaned = s.replaceAll("[\\\\/:*?\"<>|]", "_").trim();
        // Evita puntos finales y espacios sobrantes (Windows no los acepta).
        while (cleaned.endsWith(".") || cleaned.endsWith(" ")) {
            cleaned = cleaned.substring(0, cleaned.length() - 1);
        }
        return cleaned.isEmpty() ? "Sin_nombre" : cleaned;
    }

    private static String extensionOf(String filename) {
        int dot = filename.lastIndexOf('.');
        if (dot <= 0 || dot == filename.length() - 1) return "mp3";
        return filename.substring(dot + 1).toLowerCase();
    }
}
