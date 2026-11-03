package emendatium.backend.enrichment;

import emendatium.backend.config.EnricherProperties;
import emendatium.backend.fingerprint.AcoustIdMatch;
import emendatium.backend.fingerprint.FingerprintService;
import emendatium.backend.naming.FilenameBuilder;
import emendatium.backend.naming.TitleBuilder;
import emendatium.backend.shared.FilenameParser;
import emendatium.backend.shared.TrackMetadata;
import emendatium.backend.sources.EnrichmentContext;
import emendatium.backend.sources.MetadataSource;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class EnrichmentService {

    private final FingerprintService fingerprintService;
    private final List<MetadataSource> sources;
    private final MetadataMerger merger;
    private final FilenameBuilder filenameBuilder;
    private final TitleBuilder titleBuilder;
    private final EnricherProperties props;
    private final MeterRegistry meterRegistry;

    public record EnrichmentResult(TrackMetadata metadata, Suggestions suggestions) {}
    public record Suggestions(String fileName, String title, boolean hasIdentity) {}

    public EnrichmentResult enrich(Path mp3) {
        log.info("═══════════════════════════════════════════════════════════");
        log.info("🎵 ENRIQUECIENDO: {}", mp3.getFileName());
        log.info("═══════════════════════════════════════════════════════════");

        // ─── PASO 1: Parsear nombre del archivo ───
        FilenameParser.Parsed fileHint = FilenameParser.parse(mp3);
        log.info("📁 PARSE del nombre del archivo:");
        log.info("    → Artistas detectados: {}", fileHint.artists());
        log.info("    → Título detectado:    '{}'", fileHint.title());
        log.info("    → hasArtist():         {}", fileHint.hasArtist());

        // ─── PASO 2: Fingerprint con AcoustID ───
        log.info("🔍 Iniciando fingerprint...");
        Optional<AcoustIdMatch> match = fingerprintService.identify(mp3);

        if (match.isPresent()) {
            AcoustIdMatch m = match.get();
            log.info("🎯 AcoustID identificó:");
            log.info("    → Recording MBID: {}", m.recordingMbid());
            log.info("    → Artist MBID:    {}", m.artistMbid());
            log.info("    → Artista API:    '{}'", m.artist());
            log.info("    → Título API:     '{}'", m.title());
            log.info("    → Score:          {}", m.score());
        } else {
            log.info("🚫 AcoustID NO identificó la canción");
        }

        // ─── PASO 3: VERIFICACIÓN CRÍTICA (artista archivo vs API) ───
        if (match.isPresent() && fileHint.hasArtist()) {
            String apiArtist = match.get().artist();
            log.info("🔒 VERIFICACIÓN de artista:");
            log.info("    → Archivo:   {}", fileHint.artists());
            log.info("    → API dice:  '{}'", apiArtist);

            boolean coincide = FilenameParser.anyArtistMatches(fileHint.artists(), apiArtist);
            log.info("    → ¿Coincide? {}", coincide);

            if (!coincide) {
                boolean lenient = props.antiCover() != null && props.antiCover().lenient();
                if (lenient) {
                    log.warn("⚠ LENIENT: archivo dice '{}' y AcoustID dice '{}' — se acepta igualmente",
                            String.join(", ", fileHint.artists()), apiArtist);
                } else {
                    log.warn("🚫 RECHAZADO: archivo dice '{}' pero AcoustID dice '{}' — posible cover",
                            String.join(", ", fileHint.artists()), apiArtist);
                    match = Optional.empty();
                }
            } else {
                log.info("✅ ACEPTADO: los artistas coinciden");
            }
        } else if (match.isPresent() && !fileHint.hasArtist()) {
            log.info("⚠ VERIFICACIÓN saltada: nombre del archivo no tiene artista parseable");
        }

        String mbid = match.map(AcoustIdMatch::recordingMbid).orElse(null);
        String fallbackArtist = match.map(AcoustIdMatch::artist).orElse(null);
        String fallbackTitle = match.map(AcoustIdMatch::title).orElse(null);

        TrackMetadata acumulado = match
                .map(m -> TrackMetadata.builder()
                        .acoustId(m.recordingMbid())
                        .mbRecordingId(m.recordingMbid())
                        .mbArtistId(m.artistMbid())
                        .artist(m.artist())
                        .title(m.title())
                        .build())
                .orElse(TrackMetadata.empty());

        // ─── PASO 4: Cadena de peritos ───
        log.info("🔗 Ejecutando cadena de peritos...");
        for (MetadataSource source : sources) {
            Integer durationSec = acumulado.durationMs() != null
                    ? (int) (acumulado.durationMs() / 1000L) : null;
            var context = new EnrichmentContext(
                    mbid, fallbackArtist, fallbackTitle,
                    durationSec, mp3, acumulado);

            Optional<TrackMetadata> partial = runSourceTimed(source, context);
            if (partial.isPresent()) {
                acumulado = merger.merge(acumulado, partial.get());
                log.info("  ✓ {} aportó datos", source.name());
            } else {
                log.info("  · {} sin aporte", source.name());
            }
        }

        final TrackMetadata result = acumulado;

        boolean hasIdentity = isNotBlank(result.artist()) && isNotBlank(result.title());
        String suggestedFileName = hasIdentity ? filenameBuilder.build(result) + ".mp3" : null;
        String suggestedTitle = titleBuilder.build(result);

        log.info("═══════════════════════════════════════════════════════════");
        log.info("📊 RESULTADO FINAL:");
        log.info("    → hasIdentity: {}", hasIdentity);
        log.info("    → title:       '{}'", result.title());
        log.info("    → artist:      '{}'", result.artist());
        log.info("    → album:       '{}'", result.album());
        log.info("    → fileName:    '{}'", suggestedFileName);
        log.info("═══════════════════════════════════════════════════════════");

        return new EnrichmentResult(result, new Suggestions(suggestedFileName, suggestedTitle, hasIdentity));
    }

    public static EnrichmentResult stripCoverData(EnrichmentResult full) {
        TrackMetadata m = full.metadata();
        if (m.coverArt() == null || m.coverArt().data() == null) return full;

        TrackMetadata stripped = m.toBuilder()
                .coverArt(new TrackMetadata.CoverArt(
                        null,
                        m.coverArt().mimeType(),
                        m.coverArt().sourceUrl(),
                        m.coverArt().width(),
                        m.coverArt().height()))
                .build();

        return new EnrichmentResult(stripped, full.suggestions());
    }

    private boolean isNotBlank(String s) { return s != null && !s.isBlank(); }

    /**
     * Ejecuta un perito registrando su duración y resultado en Micrometer.
     * Métrica: `enricher.source.duration` con tags `source` y `outcome`
     * (hit / miss / error). Visible en /actuator/metrics.
     */
    private Optional<TrackMetadata> runSourceTimed(MetadataSource source, EnrichmentContext context) {
        Timer.Sample sample = Timer.start(meterRegistry);
        String outcome;
        try {
            Optional<TrackMetadata> partial = source.enrich(context);
            outcome = partial.isPresent() ? "hit" : "miss";
            sample.stop(meterRegistry.timer("enricher.source.duration",
                    "source", source.name(), "outcome", outcome));
            return partial;
        } catch (RuntimeException e) {
            sample.stop(meterRegistry.timer("enricher.source.duration",
                    "source", source.name(), "outcome", "error"));
            log.warn("  ✗ {} falló: {}", source.name(), e.getMessage());
            return Optional.empty();
        }
    }
}