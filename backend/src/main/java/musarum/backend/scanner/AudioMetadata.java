package musarum.backend.scanner;

public record AudioMetadata(
        String title,
        String artistName,
        String albumTitle,
        String genreName,
        Integer trackNumber,
        Integer durationMs,
        Integer year,
        Integer bitrate,
        Integer sampleRate,
        Integer channels,
        String format,
        Long fileSize,
        Integer discNumber,
        String composer
) {}