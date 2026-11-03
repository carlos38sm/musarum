package musarum.backend.favorite;

import java.util.List;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import musarum.backend.favorite.dto.FavoriteDto;
import musarum.backend.favorite.dto.MarkFavoriteRequest;
import musarum.backend.shared.security.CurrentUserResolver;

@RestController
@RequestMapping("/favorites")
class FavoriteController {

    private final FavoriteService favoriteService;
    private final CurrentUserResolver currentUser;

    FavoriteController(FavoriteService favoriteService, CurrentUserResolver currentUser) {
        this.favoriteService = favoriteService;
        this.currentUser = currentUser;
    }

    @GetMapping
    ResponseEntity<List<FavoriteDto>> findMine() {
        return ResponseEntity.ok(favoriteService.findByUser(currentUser.currentUserId()));
    }

    @PostMapping
    ResponseEntity<FavoriteDto> mark(@Valid @RequestBody MarkFavoriteRequest request) {
        return ResponseEntity.ok(favoriteService.mark(currentUser.currentUserId(), request.songId()));
    }

    @DeleteMapping("/{songId}")
    ResponseEntity<Void> unmark(@PathVariable Long songId) {
        favoriteService.unmark(currentUser.currentUserId(), songId);
        return ResponseEntity.noContent().build();
    }
}