package musarum.backend.playlist.dto;

import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Vista detallada de una playlist.
 *
 * <p>Campos extendidos para el header enriquecido:
 * <ul>
 *   <li>{@code coverUrls}: hasta 4 covers (mosaico 2×2).</li>
 *   <li>{@code durationMs}: suma de duración de todas las entries.</li>
 *   <li>{@code topGenreName} / {@code topArtistName}: los más repetidos.</li>
 *   <li>{@code createdAt}: cuándo se creó la playlist.</li>
 *   <li>{@code songCount}: cantidad total (sin tener que mirar page.total).</li>
 * </ul>
 */
public record PlaylistDetailsDto(
        Long id,
        String name,
        String description,
        boolean isPublic,
        String ownerNick,
        Long ownerId,
        Page<PlaylistEntryDto> entries,
        List<CollaboratorRef> collaborators,
        List<String> coverUrls,
        int songCount,
        Long durationMs,
        String topGenreName,
        String topArtistName,
        Instant createdAt
) {}
