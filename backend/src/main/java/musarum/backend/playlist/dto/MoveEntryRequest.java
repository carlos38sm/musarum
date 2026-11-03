package musarum.backend.playlist.dto;

import jakarta.validation.constraints.Min;

public record MoveEntryRequest(
        @Min(0) int position
) {}
