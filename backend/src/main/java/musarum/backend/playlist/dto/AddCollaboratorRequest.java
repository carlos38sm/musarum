package musarum.backend.playlist.dto;

import jakarta.validation.constraints.NotBlank;

public record AddCollaboratorRequest(
        @NotBlank String nick
) {}
