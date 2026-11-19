package musarum.backend.user.dto;

import java.time.Instant;
import musarum.backend.user.UserRole;

public record UserResponse(
        Long id,
        String nick,
        UserRole userRole,
        Instant createdAt,
        String avatarUrl,
        boolean blocked,
        // Toggles de privacidad del usuario (Home — convivencia).
        Boolean shareNowPlaying,
        Boolean shareTaste,
        // Última conexión (login exitoso). Null si todavía no ha entrado
        // desde que se añadió la columna. Solo es informativo para el
        // panel del OWNER.
        Instant lastLoginAt
) {}