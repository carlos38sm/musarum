package emendatium.backend.naming;

import emendatium.backend.config.EnricherProperties;
import emendatium.backend.shared.TrackMetadata;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Construye el nombre de archivo aplicando la plantilla configurada. Sustituye
 * placeholders como {artist}, {title}, {album}, {track} por los valores reales.
 */
@Component
@RequiredArgsConstructor
public class FilenameBuilder {

    private final EnricherProperties props;
    private final FilenameSanitizer sanitizer;

    public String build(TrackMetadata m) {
        String plantilla = props.naming().fileTemplate();
        int maxLen = props.naming().maxFileLength();

        Map<String, String> valores = Map.of(
                "artist", nvl(m.artist()),
                "title",  nvl(m.title()),
                "album",  nvl(m.album()),
                "track",  m.trackNumber() != null ? String.format("%02d", m.trackNumber()) : "",
                "year",   m.year() != null ? m.year().toString() : ""
        );

        String resultado = plantilla;
        for (var entry : valores.entrySet()) {
            resultado = resultado.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        // Si algún placeholder quedó vacío, arreglamos separadores huérfanos
        resultado = resultado
                .replaceAll("^\\s*-\\s*", "")      // "- Título" → "Título"
                .replaceAll("\\s*-\\s*$", "")      // "Artista -" → "Artista"
                .replaceAll("\\s*-\\s*-\\s*", " - "); // "A -  - B" → "A - B"

        return sanitizer.sanitize(resultado, maxLen);
    }

    private String nvl(String s) {
        return s == null ? "" : s;
    }
}