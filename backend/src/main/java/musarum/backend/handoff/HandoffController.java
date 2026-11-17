package musarum.backend.handoff;

import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
 * Publicación y lectura del "estado de reproducción" para transferir
 * reproducción entre dispositivos del mismo usuario (o entre miembros de
 * la casa). Un POST /me/handoff publica (song + position + device);
 * un GET /handoffs lista los activos (devuelve el estado de CADA usuario).
 * El cliente que "toma el relevo" simplemente hace playQueue en local.
 *
 * Usamos SQL nativo con UPSERT (ON CONFLICT) porque solo hay un registro
 * por user_id y es más limpio que lookup + save condicional.
 */
@RestController
@RequestMapping("/handoff")
class HandoffController {

    @PersistenceContext
    private EntityManager em;

    private final SongRepository songRepo;
    private final SongMapper songMapper;
    private final CurrentUserResolver currentUser;

    HandoffController(SongRepository songRepo,
                      SongMapper songMapper,
                      CurrentUserResolver currentUser) {
        this.songRepo = songRepo;
        this.songMapper = songMapper;
        this.currentUser = currentUser;
    }

    @PostMapping("/publish")
    @Transactional
    ResponseEntity<Void> publish(@RequestBody PublishRequest req) {
        Long me = currentUser.currentUserId();
        if (req == null || req.songId == null) return ResponseEntity.badRequest().build();
        Query q = em.createNativeQuery("""
                INSERT INTO playback_handoffs
                    (user_id, song_id, position_ms, device_label, updated_at)
                VALUES (?, ?, ?, ?, NOW())
                ON CONFLICT (user_id) DO UPDATE SET
                    song_id = EXCLUDED.song_id,
                    position_ms = EXCLUDED.position_ms,
                    device_label = EXCLUDED.device_label,
                    updated_at = NOW()
                """);
        q.setParameter(1, me);
        q.setParameter(2, req.songId);
        q.setParameter(3, req.positionMs != null ? req.positionMs : 0L);
        q.setParameter(4, req.deviceLabel);
        q.executeUpdate();
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    @Transactional(readOnly = true)
    List<HandoffDto> list() {
        Long me = currentUser.currentUserId();
        // Mostramos TODOS los handoffs (user propio + resto de la casa).
        // No filtramos por propio: así el user puede "tomar el relevo"
        // desde el móvil A hacia la tablet del salón, o desde Patrick
        // hacia su otro móvil (consentido por diseño: 4 users en la red).
        @SuppressWarnings("unchecked")
        List<Object[]> rows = em.createNativeQuery("""
                SELECT h.user_id, u.nick, u.avatar_url, h.song_id,
                       h.position_ms, h.device_label, h.updated_at
                FROM playback_handoffs h
                JOIN users u ON u.id = h.user_id
                WHERE h.updated_at >= NOW() - INTERVAL '48 hours'
                ORDER BY h.updated_at DESC
                """).getResultList();
        if (rows.isEmpty()) return List.of();
        var songIds = rows.stream()
                .map(r -> ((Number) r[3]).longValue())
                .distinct()
                .toList();
        var songs = songRepo.findAllById(songIds).stream()
                .collect(java.util.stream.Collectors.toMap(Song::getId, s -> s));
        List<HandoffDto> out = new java.util.ArrayList<>(rows.size());
        for (Object[] r : rows) {
            Long userId = ((Number) r[0]).longValue();
            String nick = (String) r[1];
            String avatarUrl = (String) r[2];
            Long songId = ((Number) r[3]).longValue();
            Long positionMs = ((Number) r[4]).longValue();
            String deviceLabel = (String) r[5];
            Instant updatedAt = toInstant(r[6]);
            Song s = songs.get(songId);
            SongSummaryDto summary = s != null ? songMapper.toSummary(s) : null;
            out.add(new HandoffDto(
                    userId, nick, avatarUrl,
                    summary, positionMs, deviceLabel, updatedAt,
                    userId.equals(me)));
        }
        return out;
    }

    @DeleteMapping("/publish")
    @Transactional
    ResponseEntity<Void> clear() {
        Long me = currentUser.currentUserId();
        em.createNativeQuery("DELETE FROM playback_handoffs WHERE user_id = ?")
                .setParameter(1, me)
                .executeUpdate();
        return ResponseEntity.noContent().build();
    }

    private static Instant toInstant(Object o) {
        if (o instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (o instanceof java.time.OffsetDateTime odt) return odt.toInstant();
        return null;
    }

    public record PublishRequest(Long songId, Long positionMs, String deviceLabel) {}

    public record HandoffDto(
            Long userId,
            String userNick,
            String userAvatarUrl,
            SongSummaryDto song,
            Long positionMs,
            String deviceLabel,
            Instant updatedAt,
            boolean mine) {}
}
