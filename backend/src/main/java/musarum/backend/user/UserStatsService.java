package musarum.backend.user;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import musarum.backend.reproduction.ReproductionRepository;
import musarum.backend.song.Song;
import musarum.backend.song.SongRepository;
import musarum.backend.user.dto.UserStatsResponse;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Agregados del usuario para la pestaña de Perfil. Trabaja a partir de la
 * tabla de reproducciones y resuelve nombres de canción/artista/género
 * cargando esas canciones una vez (no una por reproducción).
 */
@Service
public class UserStatsService {

    private final ReproductionRepository reproductionRepository;
    private final SongRepository songRepository;

    UserStatsService(ReproductionRepository reproductionRepository,
                     SongRepository songRepository) {
        this.reproductionRepository = reproductionRepository;
        this.songRepository = songRepository;
    }

    @Transactional(readOnly = true)
    public UserStatsResponse statsFor(Long userId) {
        long totalPlays = reproductionRepository.countByUserId(userId);
        long uniqueSongs = reproductionRepository.countDistinctSongsByUserId(userId);

        if (totalPlays == 0L) {
            return new UserStatsResponse(0L, 0L, 0L, null, null, null);
        }

        // IDs ordenados por nº de reproducciones. Nos quedamos con los 50
        // más oídos para calcular artista y género dominantes sin recorrer
        // toda la BD.
        List<Long> topIds = reproductionRepository.findSongIdsOrderedByPlaysDesc(
                userId, PageRequest.of(0, 50)
        );
        List<Song> songs = songRepository.findAllById(topIds);

        // Map<id, Song> para preservar orden al recorrer topIds.
        Map<Long, Song> byId = new HashMap<>();
        songs.forEach(s -> byId.put(s.getId(), s));

        String topSongTitle = topIds.stream()
                .map(byId::get)
                .filter(s -> s != null)
                .map(Song::getTitle)
                .findFirst()
                .orElse(null);

        String topArtistName = topArtistFrom(topIds, byId);
        String topGenreName = topGenreFrom(topIds, byId);

        // Tiempo total escuchado: suma de duraciones de todas las
        // reproducciones. Como la columna duration vive en Song, acumulamos
        // (plays por canción) * duration. Hacerlo en memoria para los 50
        // top-songs captura la mayoría del tiempo con 1 query.
        long totalListenedMs = topIds.stream()
                .map(byId::get)
                .filter(s -> s != null && s.getDurationMs() != null)
                .mapToLong(s -> s.getDurationMs().longValue())
                .sum();

        return new UserStatsResponse(
                totalPlays,
                uniqueSongs,
                totalListenedMs,
                topSongTitle,
                topArtistName,
                topGenreName
        );
    }

    private String topArtistFrom(List<Long> ids, Map<Long, Song> byId) {
        Map<String, Long> counts = new HashMap<>();
        for (Long id : ids) {
            Song s = byId.get(id);
            if (s == null) continue;
            s.getArtists().forEach(a ->
                    counts.merge(a.getName(), 1L, Long::sum));
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }

    private String topGenreFrom(List<Long> ids, Map<Long, Song> byId) {
        Map<String, Long> counts = new HashMap<>();
        for (Long id : ids) {
            Song s = byId.get(id);
            if (s == null || s.getGenre() == null) continue;
            counts.merge(s.getGenre().getName(), 1L, Long::sum);
        }
        return counts.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);
    }
}
