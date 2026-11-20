package musarum.backend.shared.config;

import musarum.backend.shared.security.JwtAuthFilter;
import musarum.backend.shared.security.LoggingAccessDeniedHandler;
import musarum.backend.shared.security.LoggingAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

// EnableMethodSecurity activa @PreAuthorize("hasRole('OWNER')") en los
// controllers. El filtro JWT ya mete ROLE_OWNER / ROLE_MEMBER como authority.
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;

    SecurityConfig(JwtAuthFilter jwtAuthFilter) {
        this.jwtAuthFilter = jwtAuthFilter;
    }

    @Bean
    SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/auth/**").permitAll()
                .requestMatchers("/version").permitAll()
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**", "/swagger-ui.html").permitAll()
                // /error: endpoint interno al que Spring Boot hace FORWARD
                // cuando un controller lanza una excepción runtime. En modo
                // STATELESS el SecurityContext se limpia entre dispatches,
                // así que el forward llega como anónimo. Si /error exige
                // auth, el móvil recibe un 401 enmascarando el 500 real
                // (síntoma clásico: "la canción se borra pero sale 401").
                // Dejándolo permitAll, el 500 original llega al móvil con
                // su JSON y podemos diagnosticar la causa raíz de verdad.
                .requestMatchers("/error").permitAll()
                .anyRequest().authenticated())
            // Spring Security 6 por defecto devuelve 403 también para peticiones
            // sin token válido, lo que confunde "sin credenciales" con "sin
            // permisos". Forzamos 401 cuando el filtro JWT no ha podido
            // autenticar (token ausente, caducado o firma inválida). Así el
            // UnauthorizedInterceptor del móvil puede hacer logout automático
            // y re-login, en vez de mostrar 403 permanente.
            // Reemplazamos el HttpStatusEntryPoint vanilla por uno que logea
            // la causa exacta del 401 (ver LoggingAuthenticationEntryPoint).
            // Y añadimos un AccessDeniedHandler para separar 401 (sin auth)
            // de 403 (autenticado pero sin rol) — antes ambos se mezclaban
            // en 401 y era imposible saber si el token no llegaba o si
            // llegaba con rol mal.
            .exceptionHandling(ex -> ex
                    .authenticationEntryPoint(new LoggingAuthenticationEntryPoint())
                    .accessDeniedHandler(new LoggingAccessDeniedHandler()))
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }
}