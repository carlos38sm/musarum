package musarum.backend.genre;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import musarum.backend.shared.BaseEntity;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@Table(name = "genres")
public class Genre extends BaseEntity{
	
	@Column(nullable = false, unique = true)
	private String name;
}