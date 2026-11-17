package musarum.backend.party;

import java.time.Instant;
import java.util.ArrayList;
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

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;

import musarum.backend.shared.security.CurrentUserResolver;
import musarum.backend.song.Song;
import musarum.backend.song.SongMapper;
import musarum.backend.song.SongRepository;
import musarum.backend.song.dto.SongSummaryDto;

/**
 * Fiestas musicales ("listening party"). Un host crea la fiesta y pone
 * música desde su móvil; los demás ven la canción actual y la cola, y
 * pueden añadir canciones al final. Solo UNA party activa a la vez
 * (garantizado en DB por partial unique).
 *
 * El móvil polea /party/active cada N segundos para enterarse de cambios
 * (añadidos nuevos, canción actual, host cambia). No hay WebSocket — con
 * 4 users en Tailscale el polling es suficiente.
 */
@RestController
@RequestMapping("/party")
class PartyController {

    @PersistenceContext
    private EntityManager em;

    private final SongRepository songRepo;
    private final SongMapper songMapper;
    private final CurrentUserResolver currentUser;

    PartyController(SongRepository songRepo,
                    SongMapper songMapper,
                    CurrentUserResolver currentUser) {
        this.songRepo = songRepo;
        this.songMapper = songMapper;
        this.currentUser = currentUser;
    }

    @PostMapping
    @Transactional
    ResponseEntity<PartyDto> create() {
        Long me = currentUser.currentUserId();
        // Si ya hay una activa, devolvemos esa (no reventar: UX más amable).
        Long existing = activePartyId();
        if (existing != null) return ResponseEntity.ok(loadParty(existing, me));

        Query ins = em.createNativeQuery("""
                INSERT INTO listening_parties
                    (created_at, updated_at, host_user_id)
                VALUES (NOW(), NOW(), ?)
                RETURNING id
                """);
        ins.setParameter(1, me);
        Long id = ((Number) ins.getSingleResult()).longValue();
        // El host se auto-añade como miembro.
        em.createNativeQuery("""
                INSERT INTO listening_party_members (party_id, user_id, joined_at)
                VALUES (?, ?, NOW())
                ON CONFLICT (party_id, user_id) DO NOTHING
                """)
                .setParameter(1, id)
                .setParameter(2, me)
                .executeUpdate();
        return ResponseEntity.ok(loadParty(id, me));
    }

    @GetMapping("/active")
    @Transactional(readOnly = true)
    ResponseEntity<PartyDto> active() {
        Long me = currentUser.currentUserId();
        Long id = activePartyId();
        if (id == null) return ResponseEntity.noContent().build();
        return ResponseEntity.ok(loadParty(id, me));
    }

    @PostMapping("/{id}/join")
    @Transactional
    ResponseEntity<Void> join(@PathVariable Long id) {
        Long me = currentUser.currentUserId();
        if (!isActive(id)) return ResponseEntity.notFound().build();
        em.createNativeQuery("""
                INSERT INTO listening_party_members (party_id, user_id, joined_at)
                VALUES (?, ?, NOW())
                ON CONFLICT (party_id, user_id) DO NOTHING
                """)
                .setParameter(1, id)
                .setParameter(2, me)
                .executeUpdate();
        touch(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/leave")
    @Transactional
    ResponseEntity<Void> leave(@PathVariable Long id) {
        Long me = currentUser.currentUserId();
        em.createNativeQuery("DELETE FROM listening_party_members WHERE party_id = ? AND user_id = ?")
                .setParameter(1, id)
                .setParameter(2, me)
                .executeUpdate();
        touch(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/queue")
    @Transactional
    ResponseEntity<Void> addToQueue(@PathVariable Long id, @RequestBody AddSongRequest req) {
        Long me = currentUser.currentUserId();
        if (!isActive(id)) return ResponseEntity.notFound().build();
        if (req == null || req.songId == null) return ResponseEntity.badRequest().build();
        if (!songRepo.existsById(req.songId)) return ResponseEntity.notFound().build();
        Integer maxPos = (Integer) em.createNativeQuery("""
                SELECT COALESCE(MAX("position"), -1)
                FROM listening_party_queue WHERE party_id = ?
                """)
                .setParameter(1, id)
                .getSingleResult();
        em.createNativeQuery("""
                INSERT INTO listening_party_queue
                    (party_id, "position", song_id, added_by_user_id, added_at)
                VALUES (?, ?, ?, ?, NOW())
                """)
                .setParameter(1, id)
                .setParameter(2, maxPos + 1)
                .setParameter(3, req.songId)
                .setParameter(4, me)
                .executeUpdate();
        touch(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/current")
    @Transactional
    ResponseEntity<Void> setCurrent(@PathVariable Long id, @RequestBody SetCurrentRequest req) {
        Long me = currentUser.currentUserId();
        if (!isActive(id)) return ResponseEntity.notFound().build();
        // Solo el host decide qué suena ahora. Ese es "el que pincha".
        if (!isHost(id, me)) return ResponseEntity.status(403).build();
        em.createNativeQuery("UPDATE listening_parties SET current_song_id = ?, updated_at = NOW() WHERE id = ?")
                .setParameter(1, req != null ? req.songId : null)
                .setParameter(2, id)
                .executeUpdate();
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    @Transactional
    ResponseEntity<Void> close(@PathVariable Long id) {
        Long me = currentUser.currentUserId();
        if (!isActive(id)) return ResponseEntity.notFound().build();
        if (!isHost(id, me)) return ResponseEntity.status(403).build();
        em.createNativeQuery("UPDATE listening_parties SET closed_at = NOW(), updated_at = NOW() WHERE id = ?")
                .setParameter(1, id)
                .executeUpdate();
        return ResponseEntity.noContent().build();
    }

    // ── Helpers ────────────────────────────────────────────────────────

    private Long activePartyId() {
        var r = em.createNativeQuery("""
                SELECT id FROM listening_parties
                WHERE closed_at IS NULL ORDER BY id DESC LIMIT 1
                """).getResultList();
        return r.isEmpty() ? null : ((Number) r.get(0)).longValue();
    }

    private boolean isActive(Long id) {
        var r = em.createNativeQuery("""
                SELECT 1 FROM listening_parties
                WHERE id = ? AND closed_at IS NULL
                """).setParameter(1, id).getResultList();
        return !r.isEmpty();
    }

    private boolean isHost(Long id, Long userId) {
        var r = em.createNativeQuery("""
                SELECT host_user_id FROM listening_parties WHERE id = ?
                """).setParameter(1, id).getResultList();
        if (r.isEmpty()) return false;
        return ((Number) r.get(0)).longValue() == userId;
    }

    private void touch(Long id) {
        em.createNativeQuery("UPDATE listening_parties SET updated_at = NOW() WHERE id = ?")
                .setParameter(1, id)
                .executeUpdate();
    }

    @SuppressWarnings("unchecked")
    private PartyDto loadParty(Long id, Long me) {
        Object[] partyRow = (Object[]) em.createNativeQuery("""
                SELECT p.id, p.host_user_id, u.nick, p.current_song_id, p.updated_at
                FROM listening_parties p
                JOIN users u ON u.id = p.host_user_id
                WHERE p.id = ?
                """).setParameter(1, id).getSingleResult();
        Long partyId = ((Number) partyRow[0]).longValue();
        Long hostId = ((Number) partyRow[1]).longValue();
        String hostNick = (String) partyRow[2];
        Long currentSongId = partyRow[3] != null ? ((Number) partyRow[3]).longValue() : null;
        Instant updatedAt = toInstant(partyRow[4]);

        List<Object[]> qRows = em.createNativeQuery("""
                SELECT q.id, q.song_id, q."position", q.added_by_user_id, u.nick
                FROM listening_party_queue q
                JOIN users u ON u.id = q.added_by_user_id
                WHERE q.party_id = ?
                ORDER BY q."position" ASC
                """).setParameter(1, id).getResultList();
        var songIds = new ArrayList<Long>();
        if (currentSongId != null) songIds.add(currentSongId);
        for (Object[] r : qRows) songIds.add(((Number) r[1]).longValue());
        Map<Long, Song> songs = songIds.isEmpty()
                ? Map.of()
                : songRepo.findAllById(songIds).stream()
                        .collect(Collectors.toMap(Song::getId, s -> s));

        List<QueueItemDto> queue = new ArrayList<>(qRows.size());
        for (Object[] r : qRows) {
            Long entryId = ((Number) r[0]).longValue();
            Long songId = ((Number) r[1]).longValue();
            int position = ((Number) r[2]).intValue();
            Long addedBy = ((Number) r[3]).longValue();
            String addedByNick = (String) r[4];
            Song s = songs.get(songId);
            queue.add(new QueueItemDto(
                    entryId, position, addedBy, addedByNick,
                    s != null ? songMapper.toSummary(s) : null));
        }

        List<Object[]> mRows = em.createNativeQuery("""
                SELECT m.user_id, u.nick, u.avatar_url
                FROM listening_party_members m
                JOIN users u ON u.id = m.user_id
                WHERE m.party_id = ?
                ORDER BY m.joined_at ASC
                """).setParameter(1, id).getResultList();
        List<MemberDto> members = new ArrayList<>(mRows.size());
        for (Object[] r : mRows) {
            members.add(new MemberDto(
                    ((Number) r[0]).longValue(),
                    (String) r[1],
                    (String) r[2]));
        }

        SongSummaryDto current = currentSongId != null
                ? songs.get(currentSongId) != null
                    ? songMapper.toSummary(songs.get(currentSongId))
                    : null
                : null;

        return new PartyDto(
                partyId, hostId, hostNick, hostId.equals(me),
                current, queue, members, updatedAt);
    }

    private static Instant toInstant(Object o) {
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return null;
    }

    public record AddSongRequest(Long songId) {}
    public record SetCurrentRequest(Long songId) {}

    public record PartyDto(
            Long id,
            Long hostUserId,
            String hostNick,
            boolean iAmHost,
            SongSummaryDto currentSong,
            List<QueueItemDto> queue,
            List<MemberDto> members,
            Instant updatedAt) {}

    public record QueueItemDto(
            Long id,
            Integer position,
            Long addedByUserId,
            String addedByNick,
            SongSummaryDto song) {}

    public record MemberDto(Long userId, String nick, String avatarUrl) {}
}
