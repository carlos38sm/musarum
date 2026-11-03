package musarum.backend.favorite;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import musarum.backend.favorite.dto.FavoriteDto;
import musarum.backend.favorite.exception.FavoriteNotFoundException;
import musarum.backend.favorite.exception.SongAlreadyFavoritedException;
import musarum.backend.song.Song;
import musarum.backend.song.SongService;
import musarum.backend.user.User;
import musarum.backend.user.UserService;

@Service
public class FavoriteService {

    private final FavoriteRepository favoriteRepository;
    private final FavoriteMapper favoriteMapper;
    private final UserService userService;
    private final SongService songService;

    FavoriteService(FavoriteRepository favoriteRepository,
                    FavoriteMapper favoriteMapper,
                    UserService userService,
                    SongService songService) {
        this.favoriteRepository = favoriteRepository;
        this.favoriteMapper = favoriteMapper;
        this.userService = userService;
        this.songService = songService;
    }

    @Transactional(readOnly = true)
    public List<FavoriteDto> findByUser(Long userId) {
        return favoriteRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(favoriteMapper::toDto)
                .toList();
    }

    @Transactional
    public FavoriteDto mark(Long userId, Long songId) {
        if (favoriteRepository.existsByUserIdAndSongId(userId, songId)) {
            throw new SongAlreadyFavoritedException(songId);
        }

        User user = userService.findEntityById(userId);
        Song song = songService.findEntityById(songId);

        Favorite favorite = Favorite.builder()
                .user(user)
                .song(song)
                .build();

        Favorite saved = favoriteRepository.save(favorite);
        return favoriteMapper.toDto(saved);
    }

    @Transactional
    public void unmark(Long userId, Long songId) {
        if (!favoriteRepository.existsByUserIdAndSongId(userId, songId)) {
            throw new FavoriteNotFoundException(songId);
        }
        favoriteRepository.deleteByUserIdAndSongId(userId, songId);
    }
    
    @Transactional(readOnly = true)
    public boolean isFavorite(Long userId, Long songId) {
        return favoriteRepository.existsByUserIdAndSongId(userId, songId);
    }
}