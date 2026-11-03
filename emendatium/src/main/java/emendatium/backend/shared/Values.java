package emendatium.backend.shared;

import java.util.Set;

/** Normaliza marcadores basura de las APIs ("none", "", "[unknown]"...) a null. */
public final class Values {

    private Values() {}

    private static final Set<String> BASURA = Set.of(
            "", "none", "null", "n/a", "na", "nan",
            "unknown", "[unknown]", "[none]", "<unknown>",
            "unknown artist", "<unknown artist>", "unknown album", "<unknown album>",
            "no artist", "no album", "no title",
            "various artists", "various", "va",
            "-", "--", "---",
            "not applicable", "undefined", "nil");

    /**
     * Devuelve el valor limpio o null si está en blanco o es un marcador basura.
     * Normaliza espacios unicode raros (NBSP, zero-width) a espacios normales
     * antes de la comprobación.
     */
    public static String clean(String s) {
        if (s == null) return null;
        String t = s
                .replace(' ', ' ')  // NBSP
                .replace(' ', ' ')  // figure space
                .replace(' ', ' ')  // narrow NBSP
                .replace('​', ' ')  // zero-width space
                .replaceAll("\\s+", " ")
                .trim();
        if (t.isEmpty()) return null;
        if (BASURA.contains(t.toLowerCase())) return null;
        return t;
    }
}
