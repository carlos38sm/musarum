package musarum.backend.reproduction;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import musarum.backend.song.SongService;
import musarum.backend.song.dto.SongSummaryDto;

@Service
public class ReproductionService {

    private final ReproductionRepository repository;
    private final SongService songService;

    ReproductionService(ReproductionRepository repository, SongService songService) {
        this.repository = repository;
        this.songService = songService;
    }

    /** Log de una reproducción. El móvil llama aquí al cumplir 30s de escucha. */
    @Transactional
    public void logPlay(Long userId, Long songId) {
        repository.save(Reproduction.of(userId, songId));
    }

    /**
     * Top de canciones del usuario. {@code range} controla desde cuándo:
     * "day" / "week" / "month" / "year" / "all" (por defecto "week").
     */
    @Transactional(readOnly = true)
    public List<SongSummaryDto> findTop(Long userId, String range, int limit) {
        Instant since = sinceFor(range);
        List<Long> ids = repository.findTopSongIds(userId, since, PageRequest.of(0, limit));
        return songService.findByIdsOrdered(ids);
    }

    @Transactional(readOnly = true)
    public List<SongSummaryDto> findRecent(Long userId, int limit) {
        List<Long> ids = repository.findRecentSongIds(userId, PageRequest.of(0, limit));
        return songService.findByIdsOrdered(ids);
    }

    @Transactional(readOnly = true)
    public List<SongSummaryDto> findLastPlayed(Long userId, int limit) {
        List<Long> ids = repository.findLastPlayedSongIds(userId, PageRequest.of(0, limit));
        return songService.findByIdsOrdered(ids);
    }

    private Instant sinceFor(String range) {
        Instant now = Instant.now();
        return switch (range == null ? "week" : range.toLowerCase()) {
            case "day"   -> now.minus(1, ChronoUnit.DAYS);
            case "week"  -> now.minus(7, ChronoUnit.DAYS);
            case "month" -> now.minus(30, ChronoUnit.DAYS);
            case "year"  -> now.minus(365, ChronoUnit.DAYS);
            case "all"   -> Instant.EPOCH;
            default      -> now.minus(7, ChronoUnit.DAYS);
        };
    }
}
