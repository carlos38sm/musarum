package musarum.backend.song;

import java.util.List;
import musarum.backend.album.Album;
import musarum.backend.genre.Genre;
import musarum.backend.shared.VideoStorage;
import musarum.backend.song.dto.ArtistRef;
import musarum.backend.song.dto.SongCardDto;
import musarum.backend.song.dto.SongDetailsDto;
import musarum.backend.song.dto.SongSummaryDto;
import org.springframework.stereotype.Component;

@Component
public class SongMapper {

    private final VideoStorage videoStorage;

    SongMapper(VideoStorage videoStorage) {
        this.videoStorage = videoStorage;
    }

    public SongSummaryDto toSummary(Song song) {
        return new SongSummaryDto(
                song.getId(),
                song.getTitle(),
                song.getDurationMs(),
                song.getTrackNumber(),
                mapArtists(song),
                effectiveCoverUrl(song)
        );
    }

    SongCardDto toCard(Song song) {
        Album album = song.getAlbum();
        return new SongCardDto(
                song.getId(),
                song.getTitle(),
                song.getDurationMs(),
                mapArtists(song),
                album != null ? album.getTitle() : null,
                album != null ? album.getId() : null,
                effectiveCoverUrl(song)
        );
    }

    /**
     * Portada efectiva de la canción: si el OWNER le puso una propia
     * (has_custom_cover = true) priorizamos ese endpoint específico;
     * si no, caemos al cover del álbum (criterio histórico).
     *
     * Cache-busting: con Cache-Control 7 días en el backend, Coil / el
     * navegador cachean por URL. Si Carlos reemplaza la portada propia,
     * la URL debe cambiar o seguiría viendo la vieja. Añadimos
     * ?v=updatedAtMs — song.updatedAt cambia cuando editamos tags o
     * cambiamos la portada; es aceptable que invalide un poco más de lo
     * estricto, 7 días de cache en covers que no cambian siguen valiendo.
     */
    private String effectiveCoverUrl(Song song) {
        if (song.hasCustomCover()) {
            long v = song.getUpdatedAt() != null
                    ? song.getUpdatedAt().toEpochMilli()
                    : song.getId();
            return "/songs/" + song.getId() + "/cover?v=" + v;
        }
        Album album = song.getAlbum();
        return album != null ? album.getCoverUrl() : null;
    }

    SongDetailsDto toDetails(Song song, boolean isFavorite) {
        Album album = song.getAlbum();
        Genre genre = song.getGenre();
        return new SongDetailsDto(
                song.getId(),
                song.getTitle(),
                song.getDurationMs(),
                song.getTrackNumber(),
                song.getMbId(),
                song.getIsrc(),
                // hasLyrics: siempre true — el backend prueba local y, si no hay,
                // cae a LrcLib en caliente desde SongService.getLyrics
                true,
                mapArtists(song),
                album != null ? album.getTitle() : null,
                album != null ? album.getId() : null,
                effectiveCoverUrl(song),
                genre != null ? genre.getName() : null,
                genre != null ? genre.getId() : null,
                isFavorite,
                song.getAudioPath(),
                song.getBitrate(),
                song.getSampleRate(),
                song.getChannels(),
                song.getFormat(),
                song.getFileSize(),
                song.getBpm(),
                song.getMusicalKey(),
                song.getReplayGain(),
                song.getYear(),
                song.getDiscNumber(),
                song.getComposer(),
                album != null ? album.getReleaseYear() : null,
                videoStorage.hasVideo(song.getId(), song.getAudioPath())
        );
    }

    private List<ArtistRef> mapArtists(Song song) {
        return song.getArtists().stream()
                .map(artist -> new ArtistRef(artist.getId(), artist.getName()))
                .toList();
    }
}