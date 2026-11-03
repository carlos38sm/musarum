package musarum.backend.library;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import musarum.backend.shared.BaseEntity;

/**
 * Sub-carpeta de la biblioteca que el OWNER ha añadido desde la app.
 * Siempre vive bajo la carpeta "paraguas" (MUSARUM_MUSIC_PATH) para que
 * Docker la vea. Si {@code active=false}, el scanner la ignora pero las
 * canciones ya importadas de esa carpeta permanecen (y sus favoritos,
 * playlists, historial, también).
 */
@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "library_folders")
public class LibraryFolder extends BaseEntity {

    @Column(name = "path", nullable = false, length = 1024, unique = true)
    private String path;

    @Column(name = "active", nullable = false)
    private boolean active;

    public void setActive(boolean active) {
        this.active = active;
    }
}
