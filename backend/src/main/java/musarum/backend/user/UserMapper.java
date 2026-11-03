package musarum.backend.user;

import org.springframework.stereotype.Component;
import musarum.backend.user.dto.UserResponse;

@Component
class UserMapper {
	UserResponse toResponse(User user) {
		// avatarUrl ya trae el cache-busting incrustado desde AvatarStorage
		// (cada subida genera un "?v={timestamp}" nuevo), no hay que añadir
		// nada aquí. Si el fichero es viejo pero la URL es la misma, Coil
		// reutiliza la caché — que es lo que queremos cuando no cambió.
		return new UserResponse(
				user.getId(),
				user.getNick(),
				user.getUserRole(),
				user.getCreatedAt(),
				user.getAvatarUrl(),
				user.isBlocked()
		);
	}
}