package musarum.backend.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import musarum.backend.user.UserRole;

/**
 * Alta de usuario. El rol es opcional: si viene null se asigna MEMBER.
 * Que el solicitante pueda poner OWNER lo valida el controller (solo un
 * OWNER puede crear OWNERs); el request solo lo transporta.
 */
public record UserCreateRequest(

        @NotBlank
        @Size(min = 3, max = 30)
        @Pattern(regexp = "^[a-zA-Z0-9_-]+$")
        String nick,

        @NotBlank
        @Size(min = 8, max = 100)
        String password,

        UserRole role
) {}