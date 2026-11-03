package emendatium.backend.naming;

import org.springframework.stereotype.Component;

/**
 * Convierte una cadena arbitraria en un nombre de archivo válido y seguro
 * en cualquier sistema operativo, conservando la legibilidad (acentos, ñ...).
 */
@Component
public class FilenameSanitizer {

    // Windows prohíbe estos caracteres en nombres de archivo
    private static final String ILEGALES = "<>:\"/\\|?*";

    // Nombres reservados en Windows (con o sin extensión)
    private static final java.util.Set<String> RESERVADOS = java.util.Set.of(
            "con", "prn", "aux", "nul",
            "com1", "com2", "com3", "com4", "com5", "com6", "com7", "com8", "com9",
            "lpt1", "lpt2", "lpt3", "lpt4", "lpt5", "lpt6", "lpt7", "lpt8", "lpt9");

    public String sanitize(String texto, int maxLength) {
        if (texto == null) return "";

        StringBuilder sb = new StringBuilder(texto.length());
        for (char c : texto.toCharArray()) {
            if (ILEGALES.indexOf(c) >= 0 || c < 0x20) {
                // Reemplazamos por espacio y luego colapsamos: mejor que borrar sin más
                sb.append(' ');
            } else {
                sb.append(c);
            }
        }

        // Colapsa espacios múltiples y recorta extremos
        String limpio = sb.toString()
                .replaceAll("\\s{2,}", " ")
                .trim();

        // Windows tampoco tolera puntos ni espacios al final
        limpio = limpio.replaceAll("[. ]+$", "");

        if (RESERVADOS.contains(limpio.toLowerCase())) {
            limpio = "_" + limpio;
        }

        if (limpio.length() > maxLength) {
            limpio = limpio.substring(0, maxLength).trim();
        }

        return limpio.isEmpty() ? "Sin_titulo" : limpio;
    }
}