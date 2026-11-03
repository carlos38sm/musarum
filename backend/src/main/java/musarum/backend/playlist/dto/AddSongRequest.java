package musarum.backend.playlist.dto;

import jakarta.validation.constraints.NotNull;

public record AddSongRequest(

        @NotNull
        Long songId
) {}