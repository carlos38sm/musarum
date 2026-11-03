package musarum.backend.reproduction;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import musarum.backend.shared.BaseEntity;

/**
 * Histórico completo de reproducciones. Una fila por cada vez que una
 * canción supera los 30s de reproducción. Los índices por (user_id,
 * created_at) hacen rápidas las queries de "top de la semana / mes".
 */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "reproductions", indexes = {
        @Index(name = "idx_reproductions_user_created", columnList = "user_id, created_at"),
        @Index(name = "idx_reproductions_user_song", columnList = "user_id, song_id")
})
public class Reproduction extends BaseEntity {

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "song_id", nullable = false)
    private Long songId;

    public static Reproduction of(Long userId, Long songId) {
        return Reproduction.builder()
                .userId(userId)
                .songId(songId)
                .build();
    }
}
