package musarum.backend.auth.dto;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(

        @NotBlank
        String nick,

        @NotBlank
        String password
) {}