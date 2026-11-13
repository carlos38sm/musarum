package musarum.backend.playlist;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import musarum.backend.shared.BaseEntity;
import musarum.backend.user.User;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@Table(name = "playlists")
class Playlist extends BaseEntity {

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "is_public", nullable = false)
    private boolean isPublic;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_id", nullable = false)
    private User owner;

    @OneToMany(mappedBy = "playlist", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("position ASC")
    @Builder.Default
    private List<PlaylistEntry> entries = new ArrayList<>();

    // Colaboradores: usuarios distintos del owner que pueden añadir/quitar
    // canciones. Many-to-many con tabla puente generada por JPA.
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "playlist_collaborators",
            joinColumns = @JoinColumn(name = "playlist_id"),
            inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    @Builder.Default
    private Set<User> collaborators = new HashSet<>();

    void updateDetails(String name, String description, boolean isPublic) {
        this.name = name;
        this.description = description;
        this.isPublic = isPublic;
    }

    boolean isCollaborator(Long userId) {
        return collaborators.stream().anyMatch(u -> u.getId().equals(userId));
    }

    boolean canEdit(Long userId) {
        return owner.getId().equals(userId) || isCollaborator(userId);
    }

    /**
     * Puede ver la playlist: el owner, los colaboradores, o cualquiera si
     * la playlist es pública. Sin esto, GET /playlists/{id} dejaba que
     * cualquier autenticado leyera playlists privadas ajenas si conocía su ID.
     */
    boolean canView(Long userId) {
        return isPublic || owner.getId().equals(userId) || isCollaborator(userId);
    }

    void addCollaborator(User user) { collaborators.add(user); }
    void removeCollaborator(Long userId) {
        collaborators.removeIf(u -> u.getId().equals(userId));
    }
}