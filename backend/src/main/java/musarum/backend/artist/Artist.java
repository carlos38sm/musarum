package musarum.backend.artist;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import musarum.backend.shared.BaseEntity;

@Entity
@Getter
@NoArgsConstructor
@SuperBuilder
@Table(name = "artists")
public class Artist extends BaseEntity{

	@Column(nullable = false)
	private String name;

	@Column(name = "name_key", length = 300)
	private String nameKey;

	@Column(name = "sort_name")
	private String sortName;

	@Column(name = "mb_id")
	private String mbId;
	private String country;

	@Column(name = "begin_year")
	private Integer beginYear;

    @Column(columnDefinition = "TEXT")
	private String biography;

	@Column(name = "image_url")
    private String imageUrl;

	private String type;

	void updateDetails(String name, String sortName, String mbId, String country,
            Integer beginYear, String biography, String imageUrl, String type) {
		this.name = name;
		this.sortName = sortName;
		this.mbId = mbId;
		this.country = country;
		this.beginYear = beginYear;
		this.biography = biography;
		this.imageUrl = imageUrl;
		this.type = type;
	}

	public void applyEnrichment(String mbId, String biography, String country, String imageUrl) {
		if (mbId != null) this.mbId = mbId;
		if (biography != null && this.biography == null) this.biography = biography;
		if (country != null && this.country == null) this.country = country;
		if (imageUrl != null) this.imageUrl = imageUrl;
	}

	public void setNameKey(String nameKey) {
		this.nameKey = nameKey;
	}
}
