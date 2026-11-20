package musarum.backend.shared.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Reemplazo del HttpStatusEntryPoint vanilla. Antes de responder 401 escribe
 * una línea rica que combina: método + path + razón del filtro JWT + estado
 * del SecurityContext + excepción de Spring. Pensado para cazar 401s
 * esporádicos sin tener que depurar en caliente — con {@code docker logs}
 * basta para entender qué falló.
 */
public class LoggingAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private static final Logger log = LoggerFactory.getLogger(LoggingAuthenticationEntryPoint.class);

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Object reason = request.getAttribute(JwtAuthFilter.REQ_ATTR_REASON);
        String authHeader = request.getHeader("Authorization");
        int tokenLen = (authHeader != null && authHeader.startsWith("Bearer "))
                ? authHeader.length() - 7 : 0;

        log.warn("401 hunt · RESP · {} {} · reason={} · auth={} · authorities={} · tokenLen={} · spring={}: {}",
                request.getMethod(),
                request.getRequestURI(),
                reason != null ? reason : "<null>",
                auth != null ? auth.getName() : "<anon>",
                auth != null ? auth.getAuthorities() : "[]",
                tokenLen,
                authException.getClass().getSimpleName(),
                authException.getMessage());

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
    }
}
