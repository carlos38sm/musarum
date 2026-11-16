package emendatium.backend.naming;

import emendatium.backend.shared.SmartCase;
import emendatium.backend.shared.TrackMetadata;
import org.springframework.stereotype.Component;

/**
 * Reconstruye el título "presentable" para el tag ID3. Toma el título
 * canónico de MusicBrainz, lo limpia de espacios dobles y le aplica
 * capitalización inteligente (preposiciones/artículos en minúsculas,
 * siglas y camelCase respetados).
 */
@Component
public class TitleBuilder {

    public String build(TrackMetadata m) {
        if (m.title() == null || m.title().isBlank()) return null;
        String cleaned = m.title().trim().replaceAll("\\s{2,}", " ");
        return SmartCase.apply(cleaned);
    }
}