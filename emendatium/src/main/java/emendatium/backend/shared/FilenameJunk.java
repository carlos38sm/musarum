package emendatium.backend.shared;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Elimina basura típica que meten los descargadores de audio en los nombres
 * de los ficheros. Apunta a patrones concretos — no a heurísticas vagas —
 * para que NUNCA borre texto legítimo de un título real (p.ej. "(Live)" se
 * mantiene; "(Official Audio)" se va).
 *
 * <p>Qué recorta:
 * <ul>
 *   <li>Dominios de descargadores: spotifydownload.com, spotifydown.com,
 *       y2mate.com, mp3juices, ytmp3, savemp3, flvto, 320ympeg…</li>
 *   <li>Marcas de "Official": (Official Audio), [Official Music Video],
 *       (Official Lyric Video)…</li>
 *   <li>Calidades: [HD], [4K], [320kbps], (High Quality)…</li>
 *   <li>Etiquetas de release: (Clean), (Explicit), [Radio Edit] cuando van
 *       al final entre corchetes/paréntesis y son el único "sufijo".</li>
 *   <li>Sufijos numéricos de descargadores: "(1)", "(2)" cuando están al
 *       FINAL seguidos de nada (artefacto de "ya existía ese nombre").</li>
 *   <li>Normaliza espacios múltiples y guiones huérfanos tras el recorte.</li>
 * </ul>
 */
public final class FilenameJunk {

    private FilenameJunk() {}

    // Dominios y marcas de servicios de descarga. Capturamos con y sin
    // separador delante ("-", "_", espacio) y con y sin paréntesis/corchetes.
    private static final List<Pattern> DOWNLOADER_DOMAINS = List.of(
            Pattern.compile("(?i)\\s*[\\[\\(\\{\\-_]?\\s*spotif[yi]?\\s*downloa?d(er)?(\\.\\w+)?\\s*[\\]\\)\\}\\-_]?"),
            Pattern.compile("(?i)\\s*[\\[\\(\\{\\-_]?\\s*(y2mate|yt2mp3|ytmp3|mp3juices?|savemp3|flvto|320ympeg|tubidy|convert2mp3|mp3skull|mp3hunter|mp3clan|freemp3cloud|mp3paw|mp3xd)(\\.\\w+)?\\s*[\\]\\)\\}\\-_]?"),
            Pattern.compile("(?i)\\s*[\\[\\(\\{\\-_]?\\s*(\\w+\\.com|\\w+\\.net|\\w+\\.org)\\s*[\\]\\)\\}\\-_]?\\s*$") // dominio al final del nombre
    );

    // Marcas "Official *" entre paréntesis/corchetes.
    private static final Pattern OFFICIAL_TAG = Pattern.compile(
            "(?i)\\s*[\\[\\(\\{]\\s*official\\s*(audio|music\\s*video|video|visualizer|lyric\\s*video|lyrics)?\\s*[\\]\\)\\}]");

    // Calidades.
    private static final Pattern QUALITY_TAG = Pattern.compile(
            "(?i)\\s*[\\[\\(\\{]\\s*(hd|hq|4k|8k|high\\s*quality|320\\s*kbps|256\\s*kbps|128\\s*kbps|lossless|flac)\\s*[\\]\\)\\}]");

    // Sufijo "(1)" / "(2)" al FINAL del nombre (artefacto de descargadores).
    private static final Pattern TRAILING_PAREN_NUMBER = Pattern.compile("\\s*\\(\\d+\\)\\s*$");

    // Guiones huérfanos y espacios múltiples al terminar.
    private static final Pattern ORPHAN_SEPARATORS = Pattern.compile("[\\-_]{1,}\\s*$|^\\s*[\\-_]{1,}");
    private static final Pattern MULTI_SPACE = Pattern.compile("\\s{2,}");

    /**
     * Devuelve la cadena sin basura. Idempotente: aplicar dos veces da lo
     * mismo que una. Nunca devuelve {@code null}; si todo se recortó, devuelve
     * cadena vacía y el caller decide qué hacer (p.ej. mantener el original).
     */
    public static String strip(String raw) {
        if (raw == null) return "";
        String s = raw;

        for (Pattern p : DOWNLOADER_DOMAINS) {
            s = p.matcher(s).replaceAll(" ");
        }
        s = OFFICIAL_TAG.matcher(s).replaceAll(" ");
        s = QUALITY_TAG.matcher(s).replaceAll(" ");
        s = TRAILING_PAREN_NUMBER.matcher(s).replaceAll("");
        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();
        s = ORPHAN_SEPARATORS.matcher(s).replaceAll("").trim();
        s = MULTI_SPACE.matcher(s).replaceAll(" ").trim();
        return s;
    }

    /**
     * Comprueba si {@code raw} contenía alguno de los patrones de basura.
     * Útil para decidir si vale la pena renombrar el fichero o el nombre
     * original ya estaba limpio.
     */
    public static boolean containsJunk(String raw) {
        if (raw == null || raw.isBlank()) return false;
        String stripped = strip(raw);
        return !stripped.equals(raw.trim());
    }
}
