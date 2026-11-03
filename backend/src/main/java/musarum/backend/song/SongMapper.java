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
        Album album = song.getAlbum();
        return new SongSummaryDto(
                song.getId(),
                song.getTitle(),
                song.getDurationMs(),
                song.getTrackNumber(),
                mapArtists(song),
                album != null ? album.getCoverUrl() : null
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
                album != null ? album.getCoverUrl() : null
        );
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
                album != null ? album.getCoverUrl() : null,
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