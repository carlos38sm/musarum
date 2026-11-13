package musarum.backend.home;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import musarum.backend.artist.Artist;
import musarum.backend.artist.ArtistRepository;
import musarum.backend.genre.Genre;
import musarum.backend.genre.GenreRepository;
import musarum.backend.home.dto.DiscoveryPageDto;
import musarum.backend.home.dto.NowPlayingUserDto;
import musarum.backend.home.dto.SmartPlaylistInfoDto;
import musarum.backend.home.dto.TasteProfileDto;
import musarum.backend.reproduction.Reproduction;
import musarum.backend.reproduction.ReproductionRepository;
import musarum.backend.song.Song;
import musarum.backend.song.SongMapper;
import musarum.backend.song.SongRepository;
import musarum.backend.song.dto.SongSummaryDto;
import musarum.backend.user.User;
import musarum.backend.user.UserRepository;

/**
 * Servicio de las secciones de "convivencia" del Home: quién escucha qué
 * ahora, qué canciones son nuevas en el catálogo, y mini-perfil de gustos
 * semanal. Cada usuario puede desactivar su participación en las dos
 * primeras con sus toggles de privacidad (shareNowPlaying / shareTaste).
 */
@Service
public class HomeService {

    /** Ventana para "está escuchando ahora". 5 min = razonable para 4 users. */
    private static final int PRESENCE_WINDOW_MIN = 5;

    private final ReproductionRepository reproductionRepository;
    private final UserRepository userRepository;
    private final SongRepository songRepository;
    private final ArtistRepository artistRepository;
    private final GenreRepository genreRepository;
    private final SongMapper songMapper;

    HomeService(ReproductionRepository reproductionRepository,
                UserRepository userRepository,
                SongRepository songRepository,
                ArtistRepository artistRepository,
                GenreRepository genreRepository,
                SongMapper songMapper) {
        this.reproductionRepository = reproductionRepository;
        this.userRepository = userRepository;
        this.songRepository = songRepository;
        this.artistRepository = artistRepository;
        this.genreRepository = genreRepository;
        this.songMapper = songMapper;
    }

    /**
     * Lista de usuarios con su última reproducción en los 5 min anteriores.
     * Excluye al propio requester y a los que apagaron {@code shareNowPlaying}.
     */
    @Transactional(readOnly = true)
    public List<NowPlayingUserDto> nowPlaying(Long requesterId) {
        Instant since = Instant.now().minus(PRESENCE_WINDOW_MIN, ChronoUnit.MINUTES);
        List<Reproduction> last = reproductionRepository.findLastPerUserSince(since);
        if (last.isEmpty()) return List.of();

        Set<Long> userIds = last.stream().map(Reproduction::getUserId).collect(Collectors.toSet());
        Set<Long> songIds = last.stream().map(Reproduction::getSongId).collect(Collectors.toSet());

        Map<Long, User> usersById = userRepository.findAllById(userIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Song> songsById = songRepository.findAllById(songIds).stream()
                .collect(Collectors.toMap(Song::getId, s -> s));

        Instant now = Instant.now();
        List<NowPlayingUserDto> out = new ArrayList<>();
        for (Reproduction r : last) {
            if (Objects.equals(r.getUserId(), requesterId)) continue;
            User u = usersById.get(r.getUserId());
            if (u == null || !u.isShareNowPlaying()) continue;
            Song s = songsById.get(r.getSongId());
            if (s == null) continue;
            out.add(new NowPlayingUserDto(
                    u.getId(),
                    u.getNick(),
                    u.getAvatarUrl(),
                    ChronoUnit.SECONDS.between(r.getCreatedAt(), now),
                    songMapper.toSummary(s)
            ));
        }
        return out;
    }

    /**
     * Página de canciones nuevas (creadas en los últimos {@code windowDays}).
     * {@code totalNew} permite al móvil pintar un badge sin recorrer la lista
     * (útil cuando entran 100 canciones y solo pintamos 12 en el carrusel).
     */
    @Transactional(readOnly = true)
    public DiscoveryPageDto discoveries(int windowDays, Pageable pageable) {
        int days = Math.max(1, Math.min(windowDays, 365));
        Instant since = Instant.now().minus(days, ChronoUnit.DAYS);
        long total = songRepository.countSince(since);
        List<SongSummaryDto> items = songRepository.findSinceOrderByCreatedDesc(since, pageable)
                .map(songMapper::toSummary)
                .getContent();
        return new DiscoveryPageDto(days, total, items);
    }

    /**
     * Mini-perfil semanal de cada usuario de la red. Excluye al requester y
     * a quien apagara {@code shareTaste}. Un usuario sin reproducciones en
     * la ventana también queda fuera (nada que mostrar).
     */
    @Transactional(readOnly = true)
    public List<TasteProfileDto> tasteProfiles(Long requesterId) {
        Instant weekAgo = Instant.now().minus(7, ChronoUnit.DAYS);
        List<User> users = userRepository.findAll();

        List<TasteProfileDto> out = new ArrayList<>();
        for (User u : users) {
            if (Objects.equals(u.getId(), requesterId)) continue;
            if (!u.isShareTaste() || u.isBlocked()) continue;

            long plays = reproductionRepository.countByUserSince(u.getId(), weekAgo);
            if (plays == 0) continue;

            TasteProfileDto.TopArtist topArtist = pickTopArtist(u.getId(), weekAgo);
            TasteProfileDto.TopGenre topGenre = pickTopGenre(u.getId(), weekAgo);
            TasteProfileDto.TopSong topSong = pickTopSong(u.getId(), weekAgo);

            out.add(new TasteProfileDto(
                    u.getId(),
                    u.getNick(),
                    u.getAvatarUrl(),
                    plays,
                    topArtist,
                    topGenre,
                    topSong
            ));
        }
        return out;
    }

    /**
     * Metadatos de las listas inteligentes disponibles. Los subtítulos los
     * calcula el móvil si quiere (aquí damos un copy genérico), pero el
     * slug es contrato — el móvil lo manda de vuelta a {@link #smartPlaylist}.
     */
    public List<SmartPlaylistInfoDto> listSmartPlaylists() {
        return List.of(
                new SmartPlaylistInfoDto("party",
                        "Fiesta",
                        "BPM alto · géneros movidos",
                        "party"),
                new SmartPlaylistInfoDto("focus",
                        "Concentración",
                        "BPM bajo · ambient y acústico",
                        "focus"),
                new SmartPlaylistInfoDto("undiscovered",
                        "Por descubrir",
                        "Canciones del catálogo que nunca escuchaste",
                        "undiscovered"),
                new SmartPlaylistInfoDto("forgotten",
                        "Olvidadas",
                        "Tuyas, pero sin escuchar hace meses",
                        "forgotten")
        );
    }

    /**
     * Genera una lista inteligente al vuelo. {@code size} capado a [5, 100].
     * Un slug desconocido devuelve lista vacía — así el móvil puede ignorar
     * sin tratar excepciones.
     */
    @Transactional(readOnly = true)
    public List<SongSummaryDto> smartPlaylist(String slug, Long userId, int size) {
        int capped = Math.max(5, Math.min(size, 100));
        List<Song> songs = switch (slug == null ? "" : slug) {
            case "party"        -> songRepository.findPartySongs(capped);
            case "focus"        -> songRepository.findFocusSongs(capped);
            case "undiscovered" -> songRepository.findUndiscoveredForUser(userId, capped);
            case "forgotten"    -> songRepository.findForgottenForUser(userId, capped);
            default             -> List.of();
        };
        return songs.stream().map(songMapper::toSummary).toList();
    }

    // ── Helpers internos ──────────────────────────────────────────────────

    private TasteProfileDto.TopArtist pickTopArtist(Long userId, Instant since) {
        List<Object[]> rows = reproductionRepository.findTopArtistForUserSince(userId, since);
        if (rows.isEmpty()) return null;
        Object[] row = rows.get(0);
        Long artistId = ((Number) row[0]).longValue();
        long plays = ((Number) row[1]).longValue();
        Artist a = artistRepository.findById(artistId).orElse(null);
        if (a == null) return null;
        return new TasteProfileDto.TopArtist(a.getId(), a.getName(), a.getImageUrl(), plays);
    }

    private TasteProfileDto.TopGenre pickTopGenre(Long userId, Instant since) {
        List<Object[]> rows = reproductionRepository.findTopGenreForUserSince(userId, since);
        if (rows.isEmpty()) return null;
        Object[] row = rows.get(0);
        Long genreId = ((Number) row[0]).longValue();
        long plays = ((Number) row[1]).longValue();
        Genre g = genreRepository.findById(genreId).orElse(null);
        if (g == null) return null;
        return new TasteProfileDto.TopGenre(g.getId(), g.getName(), plays);
    }

    private TasteProfileDto.TopSong pickTopSong(Long userId, Instant since) {
        List<Object[]> rows = reproductionRepository.findTopSongsWithCount(
                userId, since, PageRequest.of(0, 1));
        if (rows.isEmpty()) return null;
        Object[] row = rows.get(0);
        Long songId = ((Number) row[0]).longValue();
        long plays = ((Number) row[1]).longValue();
        Song s = songRepository.findById(songId).orElse(null);
        if (s == null) return null;
        String coverUrl = s.getAlbum() != null ? s.getAlbum().getCoverUrl() : null;
        String artistsLabel = s.getArtists().stream()
                .map(Artist::getName)
                .collect(Collectors.joining(", "));
        return new TasteProfileDto.TopSong(s.getId(), s.getTitle(), coverUrl, artistsLabel, plays);
    }
}
