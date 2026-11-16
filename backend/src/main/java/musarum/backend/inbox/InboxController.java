package musarum.backend.inbox;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import musarum.backend.shared.security.CurrentUserResolver;
import musarum.backend.song.Song;
import musarum.backend.song.SongMapper;
import musarum.backend.song.SongRepository;
import musarum.backend.song.dto.SongSummaryDto;
import musarum.backend.user.User;
import musarum.backend.user.UserRepository;

/**
 * Inbox del usuario actual — canciones que otros le enviaron.
 * El móvil pinta un badge en el topbar del Home con el número de "no leídos".
 */
@RestController
@RequestMapping("/me/inbox")
class InboxController {

    private final SharedSongRepository sharedRepo;
    private final SongRepository songRepo;
    private final UserRepository userRepo;
    private final SongMapper songMapper;
    private final CurrentUserResolver currentUser;

    InboxController(SharedSongRepository sharedRepo,
                    SongRepository songRepo,
                    UserRepository userRepo,
                    SongMapper songMapper,
                    CurrentUserResolver currentUser) {
        this.sharedRepo = sharedRepo;
        this.songRepo = songRepo;
        this.userRepo = userRepo;
        this.songMapper = songMapper;
        this.currentUser = currentUser;
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<InboxItemDto> list() {
        Long me = currentUser.currentUserId();
        var items = sharedRepo.findByToUserIdOrderByCreatedAtDesc(me);
        if (items.isEmpty()) return List.of();

        // Carga en lote los users y las songs referenciados — evita N+1.
        var fromIds = items.stream().map(SharedSong::getFromUserId)
                .collect(Collectors.toSet());
        var songIds = items.stream().map(SharedSong::getSongId)
                .collect(Collectors.toSet());

        Map<Long, User> users = userRepo.findAllById(fromIds).stream()
                .collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Song> songs = songRepo.findAllById(songIds).stream()
                .collect(Collectors.toMap(Song::getId, s -> s));

        return items.stream().map(it -> {
            User from = users.get(it.getFromUserId());
            Song song = songs.get(it.getSongId());
            SongSummaryDto summary = song != null ? songMapper.toSummary(song) : null;
            return new InboxItemDto(
                    it.getId(),
                    it.getFromUserId(),
                    from != null ? from.getNick() : null,
                    from != null ? from.getAvatarUrl() : null,
                    summary,
                    it.getNote(),
                    it.getCreatedAt(),
                    it.getReadAt() != null
            );
        }).toList();
    }

    @GetMapping("/unread-count")
    @Transactional(readOnly = true)
    Map<String, Long> unreadCount() {
        Long me = currentUser.currentUserId();
        return Map.of("count", sharedRepo.countByToUserIdAndReadAtIsNull(me));
    }

    @PostMapping
    @Transactional
    ResponseEntity<Void> send(@RequestBody SendShareRequest req) {
        Long me = currentUser.currentUserId();
        if (req == null || req.toUserId == null || req.songId == null) {
            return ResponseEntity.badRequest().build();
        }
        if (me.equals(req.toUserId)) return ResponseEntity.badRequest().build();
        // Validamos que existan
        if (!songRepo.existsById(req.songId)) return ResponseEntity.notFound().build();
        if (!userRepo.existsById(req.toUserId)) return ResponseEntity.notFound().build();

        SharedSong shared = SharedSong.builder()
                .fromUserId(me)
                .toUserId(req.toUserId)
                .songId(req.songId)
                .note(req.note)
                .build();
        sharedRepo.save(shared);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/read")
    @Transactional
    ResponseEntity<Void> markRead(@PathVariable Long id) {
        Long me = currentUser.currentUserId();
        var opt = sharedRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        var item = opt.get();
        if (!item.getToUserId().equals(me)) return ResponseEntity.notFound().build();
        if (item.getReadAt() == null) {
            item.markRead();
            sharedRepo.save(item);
        }
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Transactional
    ResponseEntity<Void> delete(@PathVariable Long id) {
        Long me = currentUser.currentUserId();
        var opt = sharedRepo.findById(id);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();
        var item = opt.get();
        if (!item.getToUserId().equals(me) && !item.getFromUserId().equals(me)) {
            return ResponseEntity.notFound().build();
        }
        sharedRepo.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    public record SendShareRequest(Long toUserId, Long songId, String note) {}

    public record InboxItemDto(
            Long id,
            Long fromUserId,
            String fromNick,
            String fromAvatarUrl,
            SongSummaryDto song,
            String note,
            Instant sentAt,
            boolean read) {}
}
