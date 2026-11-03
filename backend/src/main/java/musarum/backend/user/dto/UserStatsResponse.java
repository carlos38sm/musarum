package musarum.backend.user.dto;

public record UserStatsResponse(
        long totalPlays,
        long uniqueSongsPlayed,
        long totalListenedMs,
        String topSongTitle,
        String topArtistName,
        String topGenreName
) {}
