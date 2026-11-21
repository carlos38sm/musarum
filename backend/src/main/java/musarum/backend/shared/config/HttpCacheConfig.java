package musarum.backend.shared.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

// Validación condicional con ETag en los GET del catálogo. El filtro
// materializa el response en memoria, calcula el ETag (md5 del body)
// y lo mete en la cabecera. En la siguiente petición OkHttp del móvil
// manda If-None-Match y el filtro responde 304 sin body si el
// contenido no cambió. Resultado práctico: abrir SongsScreen una
// segunda vez sin cambios en la biblioteca no descarga nada.
//
// Lo limitamos a endpoints de lectura pura del catálogo. Streaming,
// portadas, avatares, letras y endpoints admin quedan FUERA: son
// binarios grandes (ETag en memoria sería caro), ya tienen sus
// propios cache headers, o necesitan devolver cuerpo siempre.
@Configuration
class HttpCacheConfig {

    @Bean
    FilterRegistrationBean<ShallowEtagHeaderFilter> etagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> frb =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        frb.setName("etagFilter");
        // Orden tras el JwtAuthFilter: el 304 solo tiene sentido con
        // el principal ya resuelto (el cuerpo depende del user en
        // algunos endpoints — favoritos, /me/plays — y el ETag es
        // body-hash, así que no filtramos por usuario a mano).
        frb.setOrder(Ordered.LOWEST_PRECEDENCE - 10);
        frb.addUrlPatterns(
                "/songs/*",
                "/albums/*",
                "/artists/*",
                "/genres/*",
                "/playlists/*",
                "/search/*",
                "/home/*",
                "/me/plays/*"
        );
        return frb;
    }
}
