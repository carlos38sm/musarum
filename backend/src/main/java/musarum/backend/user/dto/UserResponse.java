package musarum.backend.user.dto;

import java.time.Instant;
import musarum.backend.user.UserRole;

public record UserResponse(
        Long id,
        String nick,
        UserRole userRole,
        Instant createdAt,
        String avatarUrl,
        boolean blocked
) {}