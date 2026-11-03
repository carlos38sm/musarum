package emendatium.backend.shared;

import java.util.regex.Pattern;

/**
 * Utilidades para loguear sin filtrar secretos (API keys, tokens Bearer,
 * segmentos de URL que contienen la clave en servicios como TheAudioDB).
 */
public final class LogSafe {

    private LogSafe() {}

    private static final Pattern API_KEY_PARAM = Pattern.compile(
            "([?&](?:api[_-]?key|apikey|token|key|client)=)[^&\\s\"']+",
            Pattern.CASE_INSENSITIVE);

    /** Reemplaza {@code .../api/v1/json/<KEY>/...} por {@code .../api/v1/json/***}. */
    private static final Pattern THEAUDIODB_KEY = Pattern.compile(
            "(theaudiodb\\.com/api/v1/json/)[^/]+", Pattern.CASE_INSENSITIVE);

    private static final Pattern BEARER = Pattern.compile(
            "(?i)bearer\\s+[A-Za-z0-9._\\-]+");

    /** Devuelve un mensaje seguro para logs: sin API keys ni tokens. */
    public static String message(Throwable t) {
        if (t == null) return "";
        return redact(t.getMessage());
    }

    public static String redact(String s) {
        if (s == null || s.isEmpty()) return s;
        String out = API_KEY_PARAM.matcher(s).replaceAll("$1***");
        out = THEAUDIODB_KEY.matcher(out).replaceAll("$1***");
        out = BEARER.matcher(out).replaceAll("Bearer ***");
        return out;
    }
}
