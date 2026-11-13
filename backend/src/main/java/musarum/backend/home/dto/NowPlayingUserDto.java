package musarum.backend.home.dto;

import musarum.backend.song.dto.SongSummaryDto;

/**
 * Entrada de "En Musarum ahora". Un usuario que está escuchando (reproducción
 * reciente < 5 min) con la canción que sonaba al momento del log. Si el
 * usuario tiene {@code shareNowPlaying=false} no aparece aquí.
 */
public record NowPlayingUserDto(
        Long userId,
        String nick,
        String avatarUrl,
        /** Cuántos milisegundos han pasado desde la reproducción logueada. */
        Long playedSecondsAgo,
        SongSummaryDto song
) {}
