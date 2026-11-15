package musarum.backend.auth.dto;

/**
 * Perfil minimal que se muestra en el picker del login del móvil (bolitas
 * estilo Netflix). SOLO id + nick + avatarUrl — ni password, ni role, ni
 * blocked, ni timestamps. Si se añade cualquier campo aquí, revisar que
 * exponerlo públicamente no filtra datos sensibles.
 */
public record LoginUserDto(Long id, String nick, String avatarUrl) {}
