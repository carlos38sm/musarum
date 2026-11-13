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
				user.isBlocked(),
				user.isShareNowPlaying(),
				user.isShareTaste()
		);
	}

	/**
	 * Variante para búsquedas entre usuarios no-OWNER: oculta role y
	 * blocked para no filtrar info interna (quién es OWNER, quién está
	 * bloqueado). Se usa en /users/search. OWNER no gana nada con esto
	 * pero tampoco pierde — para ver la info real, va a /users/admin.
	 * Los toggles de privacidad también quedan fuera: cada uno los
	 * configura en su Perfil, no es información que deba viajar en un
	 * listado público.
	 */
	UserResponse toPublicResponse(User user) {
		return new UserResponse(
				user.getId(),
				user.getNick(),
				// Role null → el móvil nunca lo pinta en pantallas de búsqueda,
				// pero si lo hiciera, no revela OWNER.
				null,
				user.getCreatedAt(),
				user.getAvatarUrl(),
				false,
				null,
				null
		);
	}
}