package musarum.backend.user;

import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import jakarta.validation.Valid;
import musarum.backend.shared.AvatarStorage;
import musarum.backend.shared.security.CurrentUserResolver;
import musarum.backend.user.dto.ChangePasswordRequest;
import musarum.backend.user.dto.UpdateUserRequest;
import musarum.backend.user.dto.UserCreateRequest;
import musarum.backend.user.dto.UserResponse;
import musarum.backend.user.dto.UserStatsResponse;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

@RestController
@RequestMapping("/users")
class UserController {

    private final UserService userService;
    private final UserStatsService userStatsService;
    private final CurrentUserResolver currentUser;
    private final AvatarStorage avatarStorage;

    UserController(UserService userService,
                   UserStatsService userStatsService,
                   CurrentUserResolver currentUser,
                   AvatarStorage avatarStorage) {
        this.userService = userService;
        this.userStatsService = userStatsService;
        this.currentUser = currentUser;
        this.avatarStorage = avatarStorage;
    }

    // ── /me — operaciones sobre el usuario autenticado ──
    @GetMapping("/me")
    ResponseEntity<UserResponse> me() {
        return ResponseEntity.ok(userService.findById(currentUser.currentUserId()));
    }

    @GetMapping("/me/stats")
    ResponseEntity<UserStatsResponse> myStats() {
        return ResponseEntity.ok(userStatsService.statsFor(currentUser.currentUserId()));
    }

    @PatchMapping("/me")
    ResponseEntity<UserResponse> updateMe(@Valid @RequestBody UpdateUserRequest request) {
        return ResponseEntity.ok(
                userService.updateNick(currentUser.currentUserId(), request.nick())
        );
    }

    @PatchMapping("/me/password")
    ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest request) {
        userService.changePassword(
                currentUser.currentUserId(),
                request.currentPassword(),
                request.newPassword()
        );
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/search")
    ResponseEntity<List<UserResponse>> search(
            @RequestParam("q") String q,
            @RequestParam(value = "limit", defaultValue = "10") int limit
    ) {
        return ResponseEntity.ok(userService.searchByNick(q, limit));
    }

    /**
     * Alta de usuario — solo OWNER. El OWNER inicial lo crea
     * {@code OwnerBootstrap} al arrancar leyendo {@code MUSARUM_OWNER_*}
     * del entorno; de ahí para adelante, nuevas cuentas (MEMBER u OWNER)
     * las crea el OWNER desde la app.
     */
    @PostMapping
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<UserResponse> create(@Valid @RequestBody UserCreateRequest request,
                                        UriComponentsBuilder uriBuilder) {
        UserResponse created = userService.create(request);
        URI location = uriBuilder
                .path("/api/v1/users/{id}")
                .buildAndExpand(created.id())
                .toUri();
        return ResponseEntity.created(location).body(created);
    }

    // ── Avatar del usuario autenticado ──────────────────────────────────────
    @PutMapping(value = "/me/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<UserResponse> uploadMyAvatar(@RequestPart("file") MultipartFile file)
            throws IOException {
        if (file.isEmpty()) throw new IllegalArgumentException("Fichero vacío");
        if (file.getSize() > 5_000_000) {
            throw new IllegalArgumentException("Imagen demasiado grande (máx 5 MB)");
        }
        String mime = file.getContentType();
        if (mime == null || !mime.startsWith("image/")) {
            throw new IllegalArgumentException("Solo imágenes");
        }
        return ResponseEntity.ok(
                userService.setAvatar(currentUser.currentUserId(), file.getBytes(), mime)
        );
    }

    @DeleteMapping("/me/avatar")
    ResponseEntity<UserResponse> clearMyAvatar() {
        return ResponseEntity.ok(userService.clearAvatar(currentUser.currentUserId()));
    }

    /** Sirve la imagen de perfil guardada localmente. Público entre usuarios
     *  autenticados (todos pueden ver el avatar de todos — útil para
     *  colaboradores de playlist, etc.). */
    @GetMapping("/{id}/avatar")
    ResponseEntity<Resource> getAvatar(@PathVariable Long id) {
        return avatarStorage.findAvatar(id)
                .map(path -> buildImageResponse(path, avatarStorage.contentTypeFor(path)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    private ResponseEntity<Resource> buildImageResponse(Path file, String contentType) {
        try {
            long length = Files.size(file);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType(contentType))
                    .contentLength(length)
                    .cacheControl(CacheControl.maxAge(Duration.ofDays(7)).cachePublic())
                    .body(new FileSystemResource(file));
        } catch (IOException e) {
            return ResponseEntity.notFound().build();
        }
    }

    // ── Admin de usuarios (solo OWNER) ──────────────────────────────────────
    @GetMapping("/admin")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<List<UserResponse>> listAll() {
        return ResponseEntity.ok(userService.findAll());
    }

    @PatchMapping("/admin/{id}/blocked")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<UserResponse> setBlocked(@PathVariable Long id,
                                            @RequestParam("value") boolean blocked) {
        return ResponseEntity.ok(
                userService.setBlocked(id, blocked, currentUser.currentUserId())
        );
    }

    @DeleteMapping("/admin/{id}")
    @PreAuthorize("hasRole('OWNER')")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        userService.delete(id, currentUser.currentUserId());
        return ResponseEntity.noContent().build();
    }
}