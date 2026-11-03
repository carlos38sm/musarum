package musarum.backend.song.dto;

import java.util.List;

public record SongDetailsDto(
        Long id,
        String title,
        Integer durationMs,
        Integer trackNumber,
        String mbId,
        String isrc,
        boolean hasLyrics,
        List<ArtistRef> artists,
        String albumTitle,
        Long albumId,
        String coverUrl,
        String genreName,
        Long genreId,
        boolean isFavorite,
        // ── Técnicos ──
        String audioPath,
        Integer bitrate,
        Integer sampleRate,
        Integer channels,
        String format,
        Long fileSize,
        // ── Musicales ──
        Integer bpm,
        String musicalKey,
        Double replayGain,
        Integer year,
        Integer discNumber,
        String composer,
        Integer albumYear,
        boolean hasVideo
) {}