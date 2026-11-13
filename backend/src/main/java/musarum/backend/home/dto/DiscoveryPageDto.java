package musarum.backend.home.dto;

import java.util.List;

import musarum.backend.song.dto.SongSummaryDto;

/**
 * Página de "Nuevos descubrimientos". Incluye un contador global para pintar
 * el badge del Home ("12 nuevas") sin tener que contar sobre el List.items.
 *
 * El cliente paginador decide el tamaño; el backend devuelve todas las que
 * caben dentro de la ventana temporal (por defecto 30 días) ordenadas por
 * fecha de alta descendente.
 */
public record DiscoveryPageDto(
        /** Días hacia atrás que cubre esta página. */
        int windowDays,
        /** Total de canciones nuevas en la ventana (para el badge). */
        long totalNew,
        List<SongSummaryDto> items
) {}
