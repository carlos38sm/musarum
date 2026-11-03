package musarum.backend.shared;

import java.time.Instant;

public record ApiError(
        int status,
        String message,
        Instant timestamp
) {}