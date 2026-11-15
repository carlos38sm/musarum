package musarum.backend.auth;

import java.util.List;

import jakarta.validation.Valid;
import musarum.backend.auth.dto.LoginRequest;
import musarum.backend.auth.dto.LoginUserDto;
import musarum.backend.auth.dto.TokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
class AuthController {

    private final AuthService authService;

    AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    ResponseEntity<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /**
     * Devuelve la lista de usuarios para el "picker estilo Netflix" del login
     * del móvil. Solo id + nick + avatarUrl — NUNCA role, blocked, ni ningún
     * campo sensible: esta ruta es pública (dentro de /auth/** que ya está
     * {@code permitAll}). Tailscale ya limita quién llega al backend, así
     * que exponer el nick/avatar aquí es aceptable para el público objetivo
     * (4 users en red privada). Users bloqueados sí aparecen; si tapean su
     * bolita y la clave, el login los rechazará por {@link AuthService}.
     */
    @GetMapping("/users")
    ResponseEntity<List<LoginUserDto>> listUsers() {
        return ResponseEntity.ok(authService.listUsers());
    }
}