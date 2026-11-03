package musarum.backend.search;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import musarum.backend.album.AlbumService;
import musarum.backend.artist.ArtistService;
import musarum.backend.search.dto.SearchResultDto;
import musarum.backend.song.SongService;

@Service
public class SearchService {

    private static final int QUICK_SEARCH_LIMIT = 8;

    private final ArtistService artistService;
    private final AlbumService albumService;
    private final SongService songService;

    SearchService(ArtistService artistService,
                  AlbumService albumService,
                  SongService songService) {
        this.artistService = artistService;
        this.albumService = albumService;
        this.songService = songService;
    }

    @Transactional(readOnly = true)
    public SearchResultDto search(String query) {
        Pageable limit = PageRequest.of(0, QUICK_SEARCH_LIMIT);

        return new SearchResultDto(
                artistService.search(query, limit).getContent(),
                albumService.search(query, limit).getContent(),
                songService.search(query, limit).getContent()
        );
    }
}