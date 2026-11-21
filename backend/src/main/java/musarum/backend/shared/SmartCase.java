package musarum.backend.shared;

import java.util.Set;

/**
 * Capitaliza títulos "estilo libro": la PRIMERA y ÚLTIMA palabra en
 * mayúscula inicial; las preposiciones, artículos y conjunciones cortas
 * en minúsculas. Preserva palabras que ya estén 100% en mayúsculas
 * (siglas tipo "USA", "DJ") y las que tienen mayúsculas internas
 * ("iPhone", "MacOS"). Portado del Emendatium — cuando Emendatium no está
 * desplegado o no identifica, el scanner aplica esta misma regla sobre
 * los tags ID3 para que las canciones recién importadas salgan ya
 * razonablemente capitalizadas.
 *
 * <p>Ejemplos:
 * <pre>
 *   "the lord of the rings" → "The Lord of the Rings"
 *   "canción para el que escucha" → "Canción para el que Escucha"
 *   "DJ got us falling in love" → "DJ Got Us Falling in Love"
 * </pre>
 */
public final class SmartCase {

    // Palabras vacías ES + EN + FR en minúsculas (salvo primera / última).
    // Mantener corta: cuanto más corta la lista, más natural suena.
    // Set.of rechaza duplicados en static init y tiraría
    // NoClassDefFoundError al cargar la clase.
    private static final Set<String> LOWERCASE_WORDS = Set.of(
            // Castellano
            "a", "de", "del", "la", "las", "el", "los", "un", "una",
            "y", "e", "o", "u", "para", "por", "con", "sin", "en", "al",
            "lo", "ni", "que", "si", "se",
            // Inglés. "a" ya está arriba (castellano).
            "the", "and", "but", "or", "nor", "an", "as",
            "at", "by", "for", "in", "of", "on", "to", "up", "via", "vs", "is",
            // Francés
            "le", "les", "une", "des", "aux", "du", "et"
    );

    private SmartCase() {}

    public static String apply(String input) {
        if (input == null || input.isBlank()) return input;
        String[] tokens = input.trim().split(" ");
        StringBuilder out = new StringBuilder(input.length());
        for (int i = 0; i < tokens.length; i++) {
            String tok = tokens[i];
            if (tok.isEmpty()) continue;
            if (out.length() > 0) out.append(' ');
            out.append(capitalizeToken(tok, i == 0 || i == tokens.length - 1));
        }
        return out.toString();
    }

    /**
     * forceCapital=true en la primera y última palabra (regla Chicago
     * Manual of Style). Preserva siglas y camelCase.
     */
    private static String capitalizeToken(String token, boolean forceCapital) {
        // Siglas en MAYÚSCULAS (USA, DJ, EP, LP): se respetan.
        if (token.length() >= 2 && token.equals(token.toUpperCase()) && hasLetter(token)) {
            return token;
        }
        // Palabras con mayúscula interna (iPhone, MacOS): se respetan.
        if (hasInternalUppercase(token)) return token;

        String lower = token.toLowerCase();
        // Palabra vacía fuera de los extremos: minúsculas.
        if (!forceCapital && LOWERCASE_WORDS.contains(stripTrailingPunct(lower))) {
            return lower;
        }
        // Capitaliza el PRIMER carácter alfabético (para que "¿cómo"
        // o "(hola" queden "¿Cómo", "(Hola").
        StringBuilder b = new StringBuilder(token.length());
        boolean done = false;
        for (int i = 0; i < lower.length(); i++) {
            char c = lower.charAt(i);
            if (!done && Character.isLetter(c)) {
                b.append(Character.toUpperCase(c));
                done = true;
            } else {
                b.append(c);
            }
        }
        return b.toString();
    }

    private static boolean hasLetter(String s) {
        for (int i = 0; i < s.length(); i++) {
            if (Character.isLetter(s.charAt(i))) return true;
        }
        return false;
    }

    private static boolean hasInternalUppercase(String s) {
        if (s.length() < 2) return false;
        for (int i = 1; i < s.length(); i++) {
            if (Character.isUpperCase(s.charAt(i))) return true;
        }
        return false;
    }

    private static String stripTrailingPunct(String s) {
        int end = s.length();
        while (end > 0 && !Character.isLetterOrDigit(s.charAt(end - 1))) end--;
        return s.substring(0, end);
    }
}
