package musarum.backend.album;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import musarum.backend.album.dto.AlbumCardDto;
import musarum.backend.album.dto.AlbumDetailsDto;
import musarum.backend.album.dto.AlbumSummaryDto;
import musarum.backend.album.exception.AlbumNotFoundException;
import musarum.backend.artist.Artist;
import musarum.backend.shared.Normalizer;
import musarum.backend.song.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AlbumService {

    private static final Logger log = LoggerFactory.getLogger(AlbumService.class);

    private final AlbumRepository albumRepository;
    private final AlbumMapper albumMapper;
    private final SongRepository songRepository;

    AlbumService(AlbumRepository albumRepository,
                 AlbumMapper albumMapper,
                 SongRepository songRepository) {
        this.albumRepository = albumRepository;
        this.albumMapper = albumMapper;
        this.songRepository = songRepository;
    }

    @Transactional(readOnly = true)
    public AlbumDetailsDto findById(Long id) {
        Album album = albumRepository.findById(id)
                .orElseThrow(() -> new AlbumNotFoundException(id));
        long songs = songRepository.countByAlbumId(id);
        long duration = songRepository.sumDurationMsByAlbumId(id);
        return albumMapper.toDetails(album, songs, duration);
    }

    @Transactional(readOnly = true)
    public Page<AlbumCardDto> findAll(Pageable pageable) {
        Page<Album> page = albumRepository.findAll(pageable);
        return mapWithStats(page);
    }

    /**
     * Devuelve la discografía de un artista enriquecida con nº de canciones,
     * minutos totales y tipo deducido — en una sola consulta de agregado para
     * evitar N+1 al pintar el carrusel.
     */
    @Transactional(readOnly = true)
    public Page<AlbumCardDto> findByArtist(Long artistId, Pageable pageable) {
        Page<Album> page = albumRepository.findByArtistId(artistId, pageable);
        return mapWithStats(page);
    }

    @Transactional(readOnly = true)
    public Page<AlbumCardDto> search(String query, Pageable pageable) {
        Page<Album> page = albumRepository.findByTitleContainingIgnoreCase(query, pageable);
        return mapWithStats(page);
    }

    private Page<AlbumCardDto> mapWithStats(Page<Album> page) {
        List<Long> ids = page.getContent().stream().map(Album::getId).toList();
        if (ids.isEmpty()) return page.map(albumMapper::toCard);

        Map<Long, long[]> stats = songRepository.aggregateByAlbumIds(ids).stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> new long[] {
                                ((Number) row[1]).longValue(),
                                ((Number) row[2]).longValue()
                        }
                ));
        return page.map(a -> {
            long[] s = stats.getOrDefault(a.getId(), new long[] { 0L, 0L });
            return albumMapper.toCard(a, s[0], s[1]);
        });
    }

    @Transactional
    public Album findOrCreate(String title, Artist artist) {
        String key = Normalizer.key(title);
        return albumRepository
                .findByTitleKeyAndArtistId(key, artist.getId())
                .orElseGet(() -> albumRepository.save(
                        Album.builder()
                                .title(title.trim())
                                .titleKey(key)
                                .artist(artist)
                                .build()
                ));
    }

    /** Guarda la URL de portada sin tocar el resto de campos. */
    @Transactional
    public void setCoverUrl(Long albumId, String coverUrl) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new AlbumNotFoundException(albumId));
        album.applyEnrichment(null, null, coverUrl);
    }

    @Transactional
    public void applyEnrichment(Long albumId, String mbId, Integer releaseYear, String coverUrl) {
        Album album = albumRepository.findById(albumId)
                .orElseThrow(() -> new AlbumNotFoundException(albumId));
        if (mbId != null && !mbId.isBlank()) {
            albumRepository.findByMbId(mbId).ifPresent(existing -> {
                if (!existing.getId().equals(albumId)) {
                    log.info("Enriquecimiento: álbum {} comparte mbId {} con {}, mergeando",
                            albumId, mbId, existing.getId());
                    mergeInto(existing.getId(), albumId);
                }
            });
        }
        album.applyEnrichment(mbId, releaseYear, coverUrl);
    }

    @Transactional
    public void mergeInto(Long canonicalId, Long duplicateId) {
        if (canonicalId.equals(duplicateId)) return;
        if (!albumRepository.existsById(canonicalId) || !albumRepository.existsById(duplicateId)) {
            return;
        }
        albumRepository.reassignSongsAlbum(canonicalId, duplicateId);
        albumRepository.deleteById(duplicateId);
    }

    @Transactional
    public int dedupeAll() {
        List<Album> missing = albumRepository.findAllByTitleKeyIsNull();
        for (Album a : missing) {
            a.setTitleKey(Normalizer.key(a.getTitle()));
        }

        List<Album> all = albumRepository.findAll();
        Map<String, List<Album>> byKey = all.stream()
                .collect(Collectors.groupingBy(
                        a -> a.getArtist().getId() + "::" + Normalizer.key(a.getTitle())
                ));

        int merged = 0;
        for (Map.Entry<String, List<Album>> entry : byKey.entrySet()) {
            List<Album> group = entry.getValue();
            if (group.size() < 2) continue;

            Album canonical = group.stream()
                    .min(Comparator.comparing(Album::getId))
                    .orElseThrow();

            for (Album dup : group) {
                if (!dup.getId().equals(canonical.getId())) {
                    log.info("Dedup: fusionando álbum '{}' (id={}) en '{}' (id={})",
                            dup.getTitle(), dup.getId(), canonical.getTitle(), canonical.getId());
                    mergeInto(canonical.getId(), dup.getId());
                    merged++;
                }
            }
        }
        return merged;
    }
}
