package musarum.backend.shared.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.AccessDeniedHandler;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Antes todo iba al AuthenticationEntryPoint (y devolvía 401). Ahora
 * diferenciamos: si el usuario está autenticado pero sin el rol pedido
 * ({@code @PreAuthorize("hasRole('OWNER')")}), devolvemos 403 y lo logeamos
 * con sus authorities reales — síntoma típico de un token con claim
 * userRole raro, null o en formato inesperado.
 */
public class LoggingAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingAccessDeniedHandler.class);

    @Override
    public void handle(HttpServletRequest request,
                       HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        log.warn("403 hunt · {} {} · auth={} · authorities={} · causa: {}",
                request.getMethod(),
                request.getRequestURI(),
                auth != null ? auth.getName() : "<anon>",
                auth != null ? auth.getAuthorities() : "[]",
                ex.getMessage());

        response.setStatus(HttpStatus.FORBIDDEN.value());
    }
}
