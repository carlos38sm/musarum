package musarum.backend.home;

import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import musarum.backend.home.dto.DiscoveryPageDto;
import musarum.backend.home.dto.NowPlayingUserDto;
import musarum.backend.home.dto.SmartPlaylistInfoDto;
import musarum.backend.home.dto.TasteProfileDto;
import musarum.backend.shared.security.CurrentUserResolver;
import musarum.backend.song.dto.SongSummaryDto;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Endpoints de las tres secciones de "convivencia" del Home del móvil.
 * Todo requiere autenticación (SecurityConfig fija por defecto); los
 * toggles de privacidad por usuario se aplican dentro de HomeService.
 */
@RestController
@RequestMapping("/home")
class HomeController {

    private final HomeService service;
    private final CurrentUserResolver currentUser;

    HomeController(HomeService service, CurrentUserResolver currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    /** Qui&eacute;n est&aacute; escuchando qu&eacute; ahora (&uacute;ltimos 5 min). */
    @GetMapping("/presence")
    ResponseEntity<List<NowPlayingUserDto>> presence() {
        return ResponseEntity.ok(service.nowPlaying(currentUser.currentUserId()));
    }

    /**
     * Canciones a&ntilde;adidas al cat&aacute;logo en los &uacute;ltimos N d&iacute;as. {@code size}
     * hasta 100 para la pantalla "ver todas"; por defecto 12 para el
     * carrusel del Home.
     */
    @GetMapping("/discoveries")
    ResponseEntity<DiscoveryPageDto> discoveries(
            @RequestParam(defaultValue = "30") int windowDays,
            @RequestParam(defaultValue = "12") int size
    ) {
        int capped = Math.max(1, Math.min(size, 100));
        return ResponseEntity.ok(service.discoveries(windowDays, PageRequest.of(0, capped)));
    }

    /** Mini-perfiles semanales del resto de usuarios de la red. */
    @GetMapping("/taste-profiles")
    ResponseEntity<List<TasteProfileDto>> tasteProfiles() {
        return ResponseEntity.ok(service.tasteProfiles(currentUser.currentUserId()));
    }

    /** Lista de las listas inteligentes disponibles (metadatos). */
    @GetMapping("/smart")
    ResponseEntity<List<SmartPlaylistInfoDto>> smartPlaylists() {
        return ResponseEntity.ok(service.listSmartPlaylists());
    }

    /**
     * Genera y devuelve una lista inteligente al vuelo para el user actual.
     * {@code size} capado en 5..100 por el service.
     */
    @GetMapping("/smart/{slug}")
    ResponseEntity<List<SongSummaryDto>> smartPlaylist(
            @PathVariable String slug,
            @RequestParam(defaultValue = "30") int size
    ) {
        return ResponseEntity.ok(
                service.smartPlaylist(slug, currentUser.currentUserId(), size)
        );
    }
}
