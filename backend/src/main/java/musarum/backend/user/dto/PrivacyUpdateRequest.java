package musarum.backend.user.dto;

/**
 * Patch de los toggles de privacidad del usuario autenticado. Campos nullable
 * para poder enviar solo el que cambie (no hace falta mandar los dos). Usado
 * por PATCH /users/me/privacy.
 */
public record PrivacyUpdateRequest(
        Boolean shareNowPlaying,
        Boolean shareTaste
) {}
