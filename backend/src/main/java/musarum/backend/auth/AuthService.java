package musarum.backend.auth;

import java.util.Comparator;
import java.util.List;

import musarum.backend.auth.dto.LoginRequest;
import musarum.backend.auth.dto.LoginUserDto;
import musarum.backend.auth.dto.TokenResponse;
import musarum.backend.shared.security.JwtService;
import musarum.backend.user.User;
import musarum.backend.user.UserRepository;
import musarum.backend.user.UserService;
import musarum.backend.user.dto.UserAuthDetails;
import musarum.backend.user.exception.UserNotFoundException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UserService userService;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    AuthService(UserService userService,
                UserRepository userRepository,
                PasswordEncoder passwordEncoder,
                JwtService jwtService) {
        this.userService = userService;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    /**
     * Lista de usuarios para el picker del login (sin passwords, ni roles,
     * ni blocked, ni cualquier otro campo sensible). Ordenado por nick
     * case-insensitive para que las bolitas no bailen entre visitas.
     */
    @Transactional(readOnly = true)
    public List<LoginUserDto> listUsers() {
        return userRepository.findAll().stream()
                .sorted(Comparator.comparing(User::getNick, String.CASE_INSENSITIVE_ORDER))
                .map(u -> new LoginUserDto(u.getId(), u.getNick(), u.getAvatarUrl()))
                .toList();
    }

    public TokenResponse login(LoginRequest request) {
        UserAuthDetails user;
        try {
            user = userService.findAuthDetailsByNick(request.nick());
        } catch (UserNotFoundException e) {
            throw new InvalidCredentialsException();
        }

        if (!passwordEncoder.matches(request.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }

        // Si el OWNER le bloqueó la cuenta, rechazamos con el mismo
        // mensaje genérico que credenciales inválidas. Devolver "bloqueado"
        // explícito expondría qué nicks están registrados.
        if (user.blocked()) {
            throw new InvalidCredentialsException();
        }

        String token = jwtService.generateToken(user.nick(), user.role().name());
        // Marca "última conexión" para el panel de Usuarios del OWNER.
        // Lo hacemos best-effort — si falla (DB saturada, lo que sea), el
        // token ya está emitido y el usuario entra igual.
        try {
            userRepository.findById(user.id()).ifPresent(u -> {
                u.setLastLoginAt(java.time.Instant.now());
                userRepository.save(u);
            });
        } catch (Exception ignored) {
            // No queremos tumbar el login por esto.
        }
        return new TokenResponse(token);
    }
}