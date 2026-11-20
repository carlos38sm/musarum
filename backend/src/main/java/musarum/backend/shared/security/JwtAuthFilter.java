package musarum.backend.shared.security;

import java.io.IOException;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Filtro JWT con diagnóstico explícito del fallo. Cuando rechaza un token
 * escribe en log la RAZÓN (caducado / firma inválida / malformado / rol
 * ausente). Marca la request con el atributo {@code jwt.authReason} para
 * que {@link LoggingAuthenticationEntryPoint} lo recupere y lo loggee al
 * devolver 401 — así en {@code docker logs} tenemos una línea por 401
 * con causa raíz, sin tener que estar depurando en vivo.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);
    static final String REQ_ATTR_REASON = "jwt.authReason";

    private final JwtService jwtService;

    JwtAuthFilter(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");
        String method = request.getMethod();
        String path = request.getRequestURI();

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            request.setAttribute(REQ_ATTR_REASON, "NO_HEADER");
            filterChain.doFilter(request, response);
            return;
        }

        String token = authHeader.substring(7);

        try {
            Claims claims = jwtService.parseClaims(token);
            String nick = claims.getSubject();
            String role = claims.get("userRole", String.class);

            if (role == null || role.isBlank()) {
                log.warn("401 hunt · {} {} · token sin claim userRole (nick={}) → anonymous",
                        method, path, nick);
                request.setAttribute(REQ_ATTR_REASON, "ROLE_MISSING(nick=" + nick + ")");
                filterChain.doFilter(request, response);
                return;
            }

            var authority = new SimpleGrantedAuthority("ROLE_" + role);
            var authentication = new UsernamePasswordAuthenticationToken(
                    nick, null, List.of(authority));

            SecurityContextHolder.getContext().setAuthentication(authentication);
            // No logeamos cada OK: solo rechazos. Si todo va bien el filtro es silencioso.
        } catch (ExpiredJwtException e) {
            log.warn("401 hunt · {} {} · TOKEN CADUCADO (exp={}, now={}) → anonymous",
                    method, path, e.getClaims().getExpiration(), new java.util.Date());
            request.setAttribute(REQ_ATTR_REASON, "EXPIRED(exp=" + e.getClaims().getExpiration() + ")");
        } catch (SignatureException e) {
            log.warn("401 hunt · {} {} · FIRMA INVÁLIDA (JWT_SECRET distinto al que lo emitió): {}",
                    method, path, e.getMessage());
            request.setAttribute(REQ_ATTR_REASON, "BAD_SIGNATURE");
        } catch (MalformedJwtException e) {
            log.warn("401 hunt · {} {} · JWT MALFORMADO: {}", method, path, e.getMessage());
            request.setAttribute(REQ_ATTR_REASON, "MALFORMED");
        } catch (JwtException e) {
            log.warn("401 hunt · {} {} · token rechazado ({}): {}",
                    method, path, e.getClass().getSimpleName(), e.getMessage());
            request.setAttribute(REQ_ATTR_REASON, "JWT_ERROR(" + e.getClass().getSimpleName() + ")");
        } catch (Exception e) {
            log.warn("401 hunt · {} {} · excepción inesperada parseando token: {}",
                    method, path, e.getMessage());
            request.setAttribute(REQ_ATTR_REASON, "UNEXPECTED(" + e.getClass().getSimpleName() + ")");
        }

        filterChain.doFilter(request, response);
    }
}
