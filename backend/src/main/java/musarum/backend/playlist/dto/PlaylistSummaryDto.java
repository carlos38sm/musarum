package musarum.backend.playlist.dto;

public record PlaylistSummaryDto(
        Long id,
        String name,
        boolean isPublic,
        int songCount
) {}