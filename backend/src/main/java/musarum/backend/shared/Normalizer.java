package musarum.backend.shared;

import java.util.Locale;

/**
 * Normaliza nombres de artistas, títulos de álbumes y canciones para generar
 * una clave de deduplicación consistente: minúsculas, sin diacríticos y con
 * espacios colapsados. Se conserva la puntuación significativa para no mezclar
 * nombres distintos (ej. "Beatles" ≠ "The Beatles").
 */
public final class Normalizer {

    private Normalizer() {}

    public static String key(String value) {
        if (value == null) return "";
        String nfd = java.text.Normalizer.normalize(value, java.text.Normalizer.Form.NFD);
        String stripped = nfd.replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        return stripped
                .toLowerCase(Locale.ROOT)
                .trim()
                .replaceAll("\\s+", " ");
    }
}
