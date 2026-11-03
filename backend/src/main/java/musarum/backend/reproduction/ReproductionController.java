package musarum.backend.reproduction;

import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import musarum.backend.reproduction.dto.LogPlayRequest;
import musarum.backend.shared.security.CurrentUserResolver;
import musarum.backend.song.dto.SongSummaryDto;

@RestController
@RequestMapping("/me/plays")
class ReproductionController {

    private final ReproductionService service;
    private final CurrentUserResolver currentUser;

    ReproductionController(ReproductionService service, CurrentUserResolver currentUser) {
        this.service = service;
        this.currentUser = currentUser;
    }

    @PostMapping
    ResponseEntity<Void> log(@RequestBody LogPlayRequest request) {
        service.logPlay(currentUser.currentUserId(), request.songId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/top")
    ResponseEntity<List<SongSummaryDto>> top(
            @RequestParam(defaultValue = "week") String range,
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(
                service.findTop(currentUser.currentUserId(), range, limit)
        );
    }

    @GetMapping("/recent")
    ResponseEntity<List<SongSummaryDto>> recent(
            @RequestParam(defaultValue = "20") int limit
    ) {
        return ResponseEntity.ok(
                service.findRecent(currentUser.currentUserId(), limit)
        );
    }

    @GetMapping("/last")
    ResponseEntity<List<SongSummaryDto>> last(
            @RequestParam(defaultValue = "1") int limit
    ) {
        return ResponseEntity.ok(
                service.findLastPlayed(currentUser.currentUserId(), limit)
        );
    }
}
