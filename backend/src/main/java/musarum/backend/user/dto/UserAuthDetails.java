package musarum.backend.user.dto;

import musarum.backend.user.UserRole;

public record UserAuthDetails(
        Long id,
        String nick,
        String passwordHash,
        UserRole role,
        boolean blocked
) {}