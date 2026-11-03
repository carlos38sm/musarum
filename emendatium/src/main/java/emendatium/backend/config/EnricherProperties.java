package emendatium.backend.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "enricher")
public record EnricherProperties(
        Folders folders,
        Binaries binaries,
        Http http,
        Sources sources,
        Analysis analysis,
        Naming naming,
        AntiCover antiCover
) {

    public record Folders(String input, String batchOutput) {}

    public record Binaries(String ffmpeg, String fpcalc) {}

    public record Http(String userAgent, int timeoutSeconds) {}

    public record Sources(
            Acoustid acoustid,
            Discogs discogs,
            Lastfm lastfm,
            Theaudiodb theaudiodb,
            Musicbrainz musicbrainz
    ) {
        public record Acoustid(String apiKey) {}
        public record Discogs(String token) {}
        public record Lastfm(String apiKey) {}
        public record Theaudiodb(String apiKey) {}

        /**
         * MusicBrainz admite subir el rate limit si tienes servidor propio o
         * acceso autenticado. Por defecto 1 req/s y 1 concurrente (política
         * del servicio público). Si tienes `apiToken`, se envía como Bearer.
         *
         * {@code preferredCountry} (ISO 3166-1, p. ej. "ES", "US") sesga la
         * elección de release hacia tu región cuando haya varias ediciones
         * equivalentes. Vacío = sin preferencia.
         */
        public record Musicbrainz(
                String apiToken,
                double requestsPerSecond,
                int maxConcurrent,
                String preferredCountry
        ) {
            public Musicbrainz {
                if (requestsPerSecond <= 0) requestsPerSecond = 1.0;
                if (maxConcurrent      <= 0) maxConcurrent    = 1;
                if (preferredCountry   != null) preferredCountry = preferredCountry.trim().toUpperCase();
            }
        }
    }

    public record Analysis(int sampleRate, int bpmMin, int bpmMax) {}

    public record Naming(String fileTemplate, int maxFileLength) {}

    /**
     * Modo lenient: cuando el artista del archivo no cuadra con el de AcoustID,
     * en vez de rechazar la identificación la aceptamos y solo avisamos. Útil
     * para bibliotecas con nombres mal escritos; peligroso para covers.
     */
    public record AntiCover(boolean lenient) {}
}