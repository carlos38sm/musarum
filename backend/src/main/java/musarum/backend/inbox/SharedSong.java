package musarum.backend.inbox;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import musarum.backend.shared.BaseEntity;

/**
 * Una canción enviada por un usuario a otro ("bandeja compartida").
 * Mantenemos solo los IDs — las queries nativas del repository los juntan
 * con songs/users sin tener que cargar entidades enteras.
 */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@Table(name = "shared_songs")
public class SharedSong extends BaseEntity {

    @Column(name = "from_user_id", nullable = false)
    private Long fromUserId;

    @Column(name = "to_user_id", nullable = false)
    private Long toUserId;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    private String note;

    @Column(name = "read_at")
    private Instant readAt;

    public void markRead() {
        this.readAt = Instant.now();
    }
}
