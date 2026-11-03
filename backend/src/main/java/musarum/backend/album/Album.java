package musarum.backend.album;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import musarum.backend.artist.Artist;
import musarum.backend.shared.BaseEntity;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@Table(name = "albums")
public class Album extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(name = "title_key", length = 300)
    private String titleKey;

    private String mbId;

    @Column(name = "release_year")
    private Integer releaseYear;

    @Column(name = "cover_url")
    private String coverUrl;

    private String type;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "artist_id", nullable = false)
    private Artist artist;

    public void applyEnrichment(String mbId, Integer releaseYear, String coverUrl) {
        if (mbId != null) this.mbId = mbId;
        if (releaseYear != null) this.releaseYear = releaseYear;
        if (coverUrl != null && this.coverUrl == null) this.coverUrl = coverUrl;
    }

    public void setTitleKey(String titleKey) {
        this.titleKey = titleKey;
    }
}
