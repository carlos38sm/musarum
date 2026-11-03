package musarum.backend.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import musarum.backend.shared.BaseEntity;

@Entity
@Getter
@Setter
@SuperBuilder
@NoArgsConstructor
@Table(name = "users")
public class User extends BaseEntity{

	@Column(nullable = false, unique = true, length = 30)
	private String nick;

	@Column(nullable = false, length = 60)
	private String password;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private UserRole userRole;

	/** URL relativa estilo "/users/42/avatar"; null si sin foto subida. */
	@Column(name = "avatar_url")
	private String avatarUrl;

	/** Si true, el login rechaza aunque las credenciales sean correctas. */
	@Column(nullable = false)
	@lombok.Builder.Default
	private boolean blocked = false;
}