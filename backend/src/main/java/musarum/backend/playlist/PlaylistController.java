package musarum.backend.playlist;

import java.util.List;
import jakarta.validation.Valid;

import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import musarum.backend.playlist.dto.AddCollaboratorRequest;
import musarum.backend.playlist.dto.AddSongRequest;
import musarum.backend.playlist.dto.AddSongsBulkRequest;
import musarum.backend.playlist.dto.MoveEntryRequest;
import musarum.backend.playlist.dto.PlaylistCreateRequest;
import musarum.backend.playlist.dto.PlaylistDetailsDto;
import musarum.backend.playlist.dto.PlaylistSummaryDto;
import musarum.backend.playlist.dto.PlaylistUpdateRequest;
import musarum.backend.shared.security.CurrentUserResolver;

@RestController
@RequestMapping("/playlists")
class PlaylistController {

    private final PlaylistService playlistService;
    private final CurrentUserResolver currentUser;

    PlaylistController(PlaylistService playlistService, CurrentUserResolver currentUser) {
        this.playlistService = playlistService;
        this.currentUser = currentUser;
    }

    @GetMapping
    ResponseEntity<List<PlaylistSummaryDto>> findMine() {
        return ResponseEntity.ok(playlistService.findByOwner(currentUser.currentUserId()));
    }

    // Playlists en las que soy COLABORADOR (me invitó otro user). El
    // owner las ve en findMine; esto es el otro lado del espejo, para
    // que el móvil pueda pintar una sección "Compartidas conmigo" en
    // la Biblioteca sin que el user tenga que saber el ID.
    @GetMapping("/shared")
    ResponseEntity<List<PlaylistSummaryDto>> findSharedWithMe() {
        return ResponseEntity.ok(playlistService.findWhereCollaborator(currentUser.currentUserId()));
    }

    @GetMapping("/{id}")
    ResponseEntity<PlaylistDetailsDto> findById(@PathVariable Long id, Pageable pageable) {
        return ResponseEntity.ok(playlistService.findById(id, currentUser.currentUserId(), pageable));
    }

    @PostMapping
    ResponseEntity<PlaylistDetailsDto> create(@Valid @RequestBody PlaylistCreateRequest request) {
        return ResponseEntity.ok(playlistService.create(request, currentUser.currentUserId()));
    }

    @PutMapping("/{id}")
    ResponseEntity<PlaylistDetailsDto> update(@PathVariable Long id,
                                              @Valid @RequestBody PlaylistUpdateRequest request) {
        return ResponseEntity.ok(playlistService.update(id, request, currentUser.currentUserId()));
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable Long id) {
        playlistService.delete(id, currentUser.currentUserId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/songs")
    ResponseEntity<PlaylistDetailsDto> addSong(@PathVariable Long id,
                                              @Valid @RequestBody AddSongRequest request,
                                              Pageable pageable) {
        return ResponseEntity.ok(playlistService.addSong(id, request, currentUser.currentUserId(), pageable));
    }

    /**
     * Añadir N canciones de una vez. Lo usa la pantalla de "Añadir
     * canciones" del móvil con selección múltiple: una sola petición
     * en lugar de N round-trips.
     */
    @PostMapping("/{id}/songs/bulk")
    ResponseEntity<PlaylistDetailsDto> addSongsBulk(@PathVariable Long id,
                                                   @Valid @RequestBody AddSongsBulkRequest request,
                                                   Pageable pageable) {
        return ResponseEntity.ok(playlistService.addSongsBulk(
                id, request, currentUser.currentUserId(), pageable));
    }

    @DeleteMapping("/{id}/entries/{entryId}")
    ResponseEntity<PlaylistDetailsDto> removeSong(@PathVariable Long id,
                                                  @PathVariable Long entryId,
                                                  Pageable pageable) {
        return ResponseEntity.ok(playlistService.removeSong(id, entryId, currentUser.currentUserId(), pageable));
    }

    /**
     * Mueve una canción a otra posición (0-based) dentro de la playlist. Lo
     * usa el drag-and-drop del móvil tras soltar — no se envía durante el
     * arrastre, solo cuando el usuario fija la nueva posición final.
     */
    @PatchMapping("/{id}/entries/{entryId}/position")
    ResponseEntity<PlaylistDetailsDto> moveEntry(@PathVariable Long id,
                                                 @PathVariable Long entryId,
                                                 @Valid @RequestBody MoveEntryRequest request,
                                                 Pageable pageable) {
        return ResponseEntity.ok(playlistService.moveEntry(
                id, entryId, request.position(), currentUser.currentUserId(), pageable));
    }

    @PostMapping("/{id}/collaborators")
    ResponseEntity<PlaylistDetailsDto> addCollaborator(@PathVariable Long id,
                                                       @Valid @RequestBody AddCollaboratorRequest request,
                                                       Pageable pageable) {
        return ResponseEntity.ok(playlistService.addCollaborator(
                id, request.nick(), currentUser.currentUserId(), pageable));
    }

    @DeleteMapping("/{id}/collaborators/{userId}")
    ResponseEntity<PlaylistDetailsDto> removeCollaborator(@PathVariable Long id,
                                                          @PathVariable Long userId,
                                                          Pageable pageable) {
        return ResponseEntity.ok(playlistService.removeCollaborator(
                id, userId, currentUser.currentUserId(), pageable));
    }
}