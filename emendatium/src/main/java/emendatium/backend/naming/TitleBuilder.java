package emendatium.backend.naming;

import emendatium.backend.shared.TrackMetadata;
import org.springframework.stereotype.Component;

/**
 * Reconstruye el título "presentable" para el tag ID3. Ahora mismo confía
 * en el título de MusicBrainz (que viene canónico) y se limita a limpiar
 * espacios raros. Punto de extensión futuro: añadir "disambiguation".
 */
@Component
public class TitleBuilder {

    public String build(TrackMetadata m) {
        if (m.title() == null || m.title().isBlank()) return null;
        return m.title().trim().replaceAll("\\s{2,}", " ");
    }
}