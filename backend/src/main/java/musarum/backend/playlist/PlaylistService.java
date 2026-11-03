package musarum.backend.playlist;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import musarum.backend.playlist.dto.AddSongRequest;
import musarum.backend.playlist.dto.PlaylistCreateRequest;
import musarum.backend.playlist.dto.PlaylistDetailsDto;
import musarum.backend.playlist.dto.PlaylistSummaryDto;
import musarum.backend.playlist.dto.PlaylistUpdateRequest;
import musarum.backend.playlist.exception.PlaylistNotFoundException;
import musarum.backend.playlist.exception.SongAlreadyInPlaylistException;
import musarum.backend.playlist.exception.SongNotInPlaylistException;
import musarum.backend.shared.security.UnauthenticatedException;
import musarum.backend.song.Song;
import musarum.backend.song.SongService;
import musarum.backend.user.User;
import musarum.backend.user.UserService;

@Service
public class PlaylistService {

    private final PlaylistRepository playlistRepository;
    private final PlaylistEntryRepository entryRepository;
    private final PlaylistMapper playlistMapper;
    private final UserService userService;
    private final SongService songService;

    PlaylistService(PlaylistRepository playlistRepository,
                    PlaylistEntryRepository entryRepository,
                    PlaylistMapper playlistMapper,
                    UserService userService,
                    SongService songService) {
        this.playlistRepository = playlistRepository;
        this.entryRepository = entryRepository;
        this.playlistMapper = playlistMapper;
        this.userService = userService;
        this.songService = songService;
    }

    @Transactional(readOnly = true)
    public List<PlaylistSummaryDto> findByOwner(Long ownerId) {
        return playlistRepository.findByOwnerId(ownerId).stream()
                .map(playlistMapper::toSummary)
                .toList();
    }

    @Transactional(readOnly = true)
    public PlaylistDetailsDto findById(Long id, Pageable pageable) {
        Playlist playlist = playlistRepository.findById(id)
                .orElseThrow(() -> new PlaylistNotFoundException(id));
        Page<PlaylistEntry> entriesPage = entryRepository.findByPlaylistIdOrderByPositionAsc(id, pageable);
        return playlistMapper.toDetails(playlist, entriesPage);
    }

    @Transactional
    public PlaylistDetailsDto create(PlaylistCreateRequest request, Long ownerId) {
        User owner = userService.findEntityById(ownerId);

        Playlist playlist = Playlist.builder()
                .name(request.name())
                .description(request.description())
                .isPublic(request.isPublic())
                .owner(owner)
                .build();

        Playlist saved = playlistRepository.save(playlist);
        return playlistMapper.toDetails(saved, Page.empty());
    }

    @Transactional
    public PlaylistDetailsDto update(Long id, PlaylistUpdateRequest request, Long requesterId) {
        Playlist playlist = playlistRepository.findByIdAndOwnerId(id, requesterId)
                .orElseThrow(() -> new PlaylistNotFoundException(id));

        playlist.updateDetails(request.name(), request.description(), request.isPublic());

        return playlistMapper.toDetails(playlist, Page.empty());
    }

    @Transactional
    public void delete(Long id, Long requesterId) {
        Playlist playlist = playlistRepository.findByIdAndOwnerId(id, requesterId)
                .orElseThrow(() -> new PlaylistNotFoundException(id));
        playlistRepository.delete(playlist);
    }

    @Transactional
    public PlaylistDetailsDto addSong(Long playlistId, AddSongRequest request, Long requesterId, Pageable pageable) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
        // Permiten editar canciones el owner y los colaboradores.
        if (!playlist.canEdit(requesterId)) throw new UnauthenticatedException();

        Song song = songService.findEntityById(request.songId());

        boolean alreadyIn = playlist.getEntries().stream()
                .anyMatch(e -> e.getSong().getId().equals(song.getId()));
        if (alreadyIn) {
            throw new SongAlreadyInPlaylistException(playlistId, song.getId());
        }

        Integer maxPosition = playlistRepository.findMaxPositionInPlaylist(playlistId);

        PlaylistEntry entry = PlaylistEntry.builder()
                .playlist(playlist)
                .song(song)
                .position(maxPosition + 1)
                .build();

        playlist.getEntries().add(entry);
        entryRepository.save(entry);

        Page<PlaylistEntry> entriesPage = entryRepository.findByPlaylistIdOrderByPositionAsc(playlistId, pageable);
        return playlistMapper.toDetails(playlist, entriesPage);
    }

    /**
     * Mueve el entry al nuevo índice (0-based) dentro de la playlist. Reordena
     * las positions del resto de entries para mantener la secuencia densa.
     * Si entryId no existe o el requester no tiene permiso de edición → excepción.
     * Si newIndex está fuera de rango se recorta al rango válido [0, size-1].
     */
    @Transactional
    public PlaylistDetailsDto moveEntry(Long playlistId, Long entryId, int newIndex,
                                        Long requesterId, Pageable pageable) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
        if (!playlist.canEdit(requesterId)) throw new UnauthenticatedException();

        // Trabajamos sobre todas las entries ordenadas (sin paginar) porque
        // reordenar sólo tiene sentido con la lista completa a mano.
        List<PlaylistEntry> ordered = new ArrayList<>(playlist.getEntries());
        ordered.sort(Comparator.comparingInt(PlaylistEntry::getPosition));

        int fromIndex = -1;
        for (int i = 0; i < ordered.size(); i++) {
            if (ordered.get(i).getId().equals(entryId)) { fromIndex = i; break; }
        }
        if (fromIndex < 0) throw new SongNotInPlaylistException(playlistId, entryId);

        int toIndex = Math.max(0, Math.min(newIndex, ordered.size() - 1));
        if (fromIndex != toIndex) {
            PlaylistEntry moved = ordered.remove(fromIndex);
            ordered.add(toIndex, moved);
            // Reasignar positions densas 0..n-1. Barato: N escrituras por move,
            // y las playlists musarum son del orden de decenas-cientos.
            for (int i = 0; i < ordered.size(); i++) {
                ordered.get(i).updatePosition(i);
            }
        }

        Page<PlaylistEntry> entriesPage = entryRepository.findByPlaylistIdOrderByPositionAsc(playlistId, pageable);
        return playlistMapper.toDetails(playlist, entriesPage);
    }

    @Transactional
    public PlaylistDetailsDto removeSong(Long playlistId, Long entryId, Long requesterId, Pageable pageable) {
        Playlist playlist = playlistRepository.findById(playlistId)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
        if (!playlist.canEdit(requesterId)) throw new UnauthenticatedException();

        PlaylistEntry entry = playlist.getEntries().stream()
                .filter(e -> e.getId().equals(entryId))
                .findFirst()
                .orElseThrow(() -> new SongNotInPlaylistException(playlistId, entryId));

        playlist.getEntries().remove(entry);

        Page<PlaylistEntry> entriesPage = entryRepository.findByPlaylistIdOrderByPositionAsc(playlistId, pageable);
        return playlistMapper.toDetails(playlist, entriesPage);
    }

    @Transactional
    public PlaylistDetailsDto addCollaborator(Long playlistId, String nick, Long requesterId, Pageable pageable) {
        Playlist playlist = playlistRepository.findByIdAndOwnerId(playlistId, requesterId)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
        User user = userService.findEntityByNick(nick.trim());
        if (user.getId().equals(playlist.getOwner().getId())) {
            throw new IllegalArgumentException("El owner ya pertenece a su propia playlist");
        }
        playlist.addCollaborator(user);
        Page<PlaylistEntry> entriesPage = entryRepository.findByPlaylistIdOrderByPositionAsc(playlistId, pageable);
        return playlistMapper.toDetails(playlist, entriesPage);
    }

    @Transactional
    public PlaylistDetailsDto removeCollaborator(Long playlistId, Long userId, Long requesterId, Pageable pageable) {
        Playlist playlist = playlistRepository.findByIdAndOwnerId(playlistId, requesterId)
                .orElseThrow(() -> new PlaylistNotFoundException(playlistId));
        playlist.removeCollaborator(userId);
        Page<PlaylistEntry> entriesPage = entryRepository.findByPlaylistIdOrderByPositionAsc(playlistId, pageable);
        return playlistMapper.toDetails(playlist, entriesPage);
    }

    @Transactional(readOnly = true)
    public List<PlaylistSummaryDto> findWhereCollaborator(Long userId) {
        return playlistRepository.findByCollaboratorId(userId).stream()
                .map(playlistMapper::toSummary)
                .toList();
    }
}