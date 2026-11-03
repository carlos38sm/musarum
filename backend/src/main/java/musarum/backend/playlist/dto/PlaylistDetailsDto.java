package musarum.backend.playlist.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record PlaylistDetailsDto(
        Long id,
        String name,
        String description,
        boolean isPublic,
        String ownerNick,
        Long ownerId,
        Page<PlaylistEntryDto> entries,
        List<CollaboratorRef> collaborators
) {}