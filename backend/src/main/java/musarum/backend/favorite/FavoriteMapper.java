package musarum.backend.favorite;

import java.util.List;
import org.springframework.stereotype.Component;
import musarum.backend.album.Album;
import musarum.backend.favorite.dto.FavoriteDto;
import musarum.backend.song.Song;
import musarum.backend.song.dto.ArtistRef;

@Component
class FavoriteMapper {

    FavoriteDto toDto(Favorite favorite) {
        Song song = favorite.getSong();
        Album album = song.getAlbum();

        List<ArtistRef> artists = song.getArtists().stream()
                .map(a -> new ArtistRef(a.getId(), a.getName()))
                .toList();

        return new FavoriteDto(
                favorite.getId(),
                song.getId(),
                song.getTitle(),
                song.getDurationMs(),
                artists,
                album != null ? album.getTitle() : null,
                album != null ? album.getCoverUrl() : null,
                favorite.getCreatedAt()
        );
    }
}