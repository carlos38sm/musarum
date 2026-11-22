package musarum.backend.playlist.dto;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

/**
 * Añadir N canciones a una playlist en una sola petición. Las que ya
 * estén en la playlist se saltan en silencio — no es error. El orden de
 * la lista fija el orden en el que se añaden al final.
 */
public record AddSongsBulkRequest(

        @NotNull
        @NotEmpty
        List<Long> songIds
) {}
