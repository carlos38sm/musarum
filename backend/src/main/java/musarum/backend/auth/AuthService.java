package musarum.backend.auth;

import musarum.backend.auth.dto.LoginRequest;
import musarum.backend.auth.dto.TokenResponse;
import musarum.backend.shared.security.JwtService;
import musarum.backend.user.UserService;
import musarum.backend.user.dto.UserAuthDetails;
import musarum.backend.user.exception.UserNotFoundException;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserService userService;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    AuthService(UserService userService,
                PasswordEncoder passwordEncoder,
                JwtService jwtService) {
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
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
        return new TokenResponse(token);
    }
}