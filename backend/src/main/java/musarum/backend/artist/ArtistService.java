package musarum.backend.artist;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import musarum.backend.album.AlbumRepository;
import musarum.backend.artist.dto.ArtistDetailsDto;
import musarum.backend.artist.dto.ArtistSummaryDto;
import musarum.backend.artist.exception.ArtistNotFoundException;
import musarum.backend.shared.Normalizer;
import musarum.backend.song.SongRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ArtistService {

    private static final Logger log = LoggerFactory.getLogger(ArtistService.class);

    private final ArtistRepository artistRepository;
    private final ArtistMapper artistMapper;
    private final SongRepository songRepository;
    private final AlbumRepository albumRepository;

    ArtistService(ArtistRepository artistRepository,
                  ArtistMapper artistMapper,
                  SongRepository songRepository,
                  AlbumRepository albumRepository) {
        this.artistRepository = artistRepository;
        this.artistMapper = artistMapper;
        this.songRepository = songRepository;
        this.albumRepository = albumRepository;
    }

    @Transactional(readOnly = true)
    public ArtistDetailsDto findById(Long id) {
        Artist artist = artistRepository.findById(id)
                .orElseThrow(() -> new ArtistNotFoundException(id));
        long songs = songRepository.countByArtistId(id);
        long albums = albumRepository.countByArtistId(id);
        long duration = songRepository.sumDurationMsByArtistId(id);
        return artistMapper.toDetails(artist, songs, albums, duration);
    }

    @Transactional(readOnly = true)
    public Page<ArtistSummaryDto> findAll(Pageable pageable) {
        return artistRepository.findAll(pageable)
                .map(artistMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<ArtistSummaryDto> search(String query, Pageable pageable) {
        return artistRepository.findByNameContainingIgnoreCase(query, pageable)
                .map(artistMapper::toSummary);
    }

    @Transactional
    public Artist findOrCreate(String name) {
        String key = Normalizer.key(name);
        return artistRepository.findByNameKey(key)
                .orElseGet(() -> artistRepository.save(
                        Artist.builder()
                                .name(name.trim())
                                .nameKey(key)
                                .build()
                ));
    }

    /** Guarda la URL de imagen sin tocar el resto de campos. */
    @Transactional
    public void setImageUrl(Long artistId, String imageUrl) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new ArtistNotFoundException(artistId));
        artist.applyEnrichment(null, null, null, imageUrl);
    }

    @Transactional
    public void applyEnrichment(Long artistId, String mbId, String biography,
                                String country, String imageUrl) {
        Artist artist = artistRepository.findById(artistId)
                .orElseThrow(() -> new ArtistNotFoundException(artistId));
        if (mbId != null && !mbId.isBlank()) {
            artistRepository.findByMbId(mbId).ifPresent(existing -> {
                if (!existing.getId().equals(artistId)) {
                    log.info("Enriquecimiento: artista {} comparte mbId {} con {}, mergeando",
                            artistId, mbId, existing.getId());
                    mergeInto(existing.getId(), artistId);
                }
            });
        }
        artist.applyEnrichment(mbId, biography, country, imageUrl);
    }

    /**
     * Reasigna canciones y álbumes del artista duplicado al canónico y borra el duplicado.
     * No hace nada si ambos ids son iguales.
     */
    @Transactional
    public void mergeInto(Long canonicalId, Long duplicateId) {
        if (canonicalId.equals(duplicateId)) return;
        if (!artistRepository.existsById(canonicalId) || !artistRepository.existsById(duplicateId)) {
            return;
        }
        artistRepository.reassignSongArtists(canonicalId, duplicateId);
        artistRepository.deleteSongArtistsForArtist(duplicateId);
        artistRepository.reassignAlbumsArtist(canonicalId, duplicateId);
        artistRepository.deleteById(duplicateId);
    }

    /**
     * Rellena nameKey en los artistas sin ella y fusiona los duplicados cuya
     * clave normalizada coincide. Devuelve cuántos artistas se fusionaron.
     */
    @Transactional
    public int dedupeAll() {
        List<Artist> missingKey = artistRepository.findAllByNameKeyIsNull();
        for (Artist a : missingKey) {
            a.setNameKey(Normalizer.key(a.getName()));
        }

        List<Artist> all = artistRepository.findAll();
        Map<String, List<Artist>> byKey = all.stream()
                .collect(Collectors.groupingBy(a -> Normalizer.key(a.getName())));

        int merged = 0;
        for (Map.Entry<String, List<Artist>> entry : byKey.entrySet()) {
            List<Artist> group = entry.getValue();
            if (group.size() < 2) continue;

            Artist canonical = group.stream()
                    .min(Comparator.comparing(Artist::getId))
                    .orElseThrow();

            for (Artist dup : group) {
                if (!dup.getId().equals(canonical.getId())) {
                    log.info("Dedup: fusionando artista '{}' (id={}) en '{}' (id={})",
                            dup.getName(), dup.getId(), canonical.getName(), canonical.getId());
                    mergeInto(canonical.getId(), dup.getId());
                    merged++;
                }
            }
        }
        return merged;
    }
}
