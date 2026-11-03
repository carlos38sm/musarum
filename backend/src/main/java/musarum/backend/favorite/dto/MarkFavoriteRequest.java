package musarum.backend.favorite.dto;

import jakarta.validation.constraints.NotNull;

public record MarkFavoriteRequest(

        @NotNull
        Long songId
) {}