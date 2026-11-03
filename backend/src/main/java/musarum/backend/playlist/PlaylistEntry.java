package musarum.backend.playlist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import musarum.backend.shared.BaseEntity;
import musarum.backend.song.Song;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@Table(
        name = "playlist_entries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"playlist_id", "song_id"})
)
class PlaylistEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "playlist_id", nullable = false)
    private Playlist playlist;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "song_id", nullable = false)
    private Song song;

    @Column(nullable = false)
    private Integer position;

    void updatePosition(Integer position) {
        this.position = position;
    }
}