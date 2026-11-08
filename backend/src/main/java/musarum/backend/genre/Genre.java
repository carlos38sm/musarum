package musarum.backend.genre;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.BatchSize;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;
import musarum.backend.shared.BaseEntity;

@Entity
@Getter
@SuperBuilder
@NoArgsConstructor
@Table(name = "genres")
// @BatchSize a nivel clase: lotes de 100 para resolver todos los géneros
// de una página de canciones en una query en vez de N.
@BatchSize(size = 100)
public class Genre extends BaseEntity{
	
	@Column(nullable = false, unique = true)
	private String name;
}